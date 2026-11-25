package com.family.medbox.domain

import com.family.medbox.data.model.MedicationPlan
import com.family.medbox.data.model.Medicine
import com.family.medbox.data.model.Member
import com.family.medbox.data.model.StockStatus
import com.family.medbox.util.DateUtils

/**
 * 端侧药箱智能体：意图识别 → 工具调用 → 结果整合 → 行动建议。
 * 每次推理都会记录 Step，前端像 agent 一样展示调用轨迹；不联网、不做诊断。
 */
class AgentBrain(
    private val knowledge: DrugKnowledge,
    private val guard: ShoppingGuard,
    private val insightEngine: InsightEngine
) {

    enum class Tone { NORMAL, GOOD, WARN, DANGER }

    /** 一次工具调用或推理阶段，前端按顺序展示。 */
    data class Step(val label: String, val detail: String = "")

    /** 从一句自然语言里解析出的服药计划草案。 */
    data class PlanDraft(
        val medicineName: String,
        val dose: String = "1 份",
        val times: List<String> = listOf("08:00"),
        val days: Int = 7,
        val memberName: String = ""
    )

    /** 回答后可以直接执行的下一步动作。 */
    data class Action(
        val label: String,
        val kind: Kind,
        val medicineId: Long = 0L,
        val medicineName: String = "",
        val filter: String = "ALL",
        val plan: PlanDraft? = null,
        /** 购物清单类动作携带的药品名，由界面层写入本地清单。 */
        val items: List<String> = emptyList()
    ) {
        enum class Kind {
            OPEN_MEDICINE, OPEN_MEDICINES, OPEN_REMINDERS, ADD_PLAN, ADD_MEDICINE,
            CREATE_PLAN, EDIT_PLAN, ADD_TO_SHOPPING, CLEAR_SHOPPING
        }
    }

    /** 多轮上下文：记住上一轮在聊哪种药、哪个成分、哪位成员。 */
    data class Memory(
        val medicine: Medicine? = null,
        val ingredient: String? = null,
        val member: Member? = null
    )

    data class Reply(
        val text: String,
        val medicines: List<Medicine> = emptyList(),
        val suggestions: List<String> = emptyList(),
        val tone: Tone = Tone.NORMAL,
        val steps: List<Step> = emptyList(),
        val actions: List<Action> = emptyList(),
        val candidates: List<String> = emptyList(),
        val memory: Memory = Memory()
    )

    private data class DrugHit(
        val name: String,
        val ingredient: String,
        val entry: DrugKnowledge.Entry?,
        val index: Int
    )

    fun greetingReply(memory: Memory = Memory()): Reply {
        val last = memory.medicine
        val hint = if (last != null) {
            "上次你在问「${last.name}」，可以接着说「那它还剩多少」或「快过期了吗」。\n\n"
        } else {
            ""
        }
        return Reply(
            text = hint + "我是家庭药师，会先查药箱再回答：\n" +
                "· 家里还有没有布洛芬？\n" +
                "· 全家人的药会不会冲突？\n" +
                "· 最近谁总忘记吃药？\n" +
                "· 下周出差 3 天带什么药",
            suggestions = listOf("家里还有布洛芬吗", "全家用药安全巡检", "最近我按时吃药了吗"),
            steps = listOf(Step("唤醒智能体", "本地推理已就绪"))
        )
    }

    fun answer(
        input: String,
        medicines: List<Medicine>,
        members: List<Member>,
        plans: List<MedicationPlan> = emptyList(),
        todayChecked: Set<String> = emptySet(),
        checks: Map<String, Set<String>> = emptyMap(),
        shoppingItems: List<String> = emptyList(),
        memory: Memory = Memory()
    ): Reply {
        val query = input.trim()
        if (query.isEmpty()) return greetingReply(memory)

        val steps = mutableListOf<Step>()
        if (containsAny(query, listOf("你好", "您好", "hi", "hello", "在吗"))) {
            return greetingReply(memory)
        }
        if (containsAny(query, listOf("能做什么", "会什么", "有哪些功能", "怎么用你", "帮助"))) {
            return abilityReply()
        }

        var hits = detectDrugs(query, medicines)
        if (hits.isEmpty() && isFollowUp(query) && memory.medicine != null) {
            hits = listOf(
                DrugHit(
                    name = memory.medicine.name,
                    ingredient = memory.medicine.ingredient.ifBlank { memory.medicine.name },
                    entry = knowledge.entryOf(memory.medicine.ingredient),
                    index = 0
                )
            )
            steps += Step("承接上一轮", memory.medicine.name)
        }
        val focus = hits.firstOrNull()

        // 0. 自然语言执行：直接生成服药计划草案
        val planIntent =
            (focus != null && containsAny(
                query,
                listOf("设提醒", "设置提醒", "设成", "设置成", "加计划", "添加计划", "建计划", "定时", "安排", "每天", "早晚", "提醒")
            )) || containsAny(query, listOf("加计划", "添加计划", "建计划", "设提醒"))
        if (planIntent) {
            return planDraftReply(query, focus, members, steps)
        }

        // 0.2 四个专职 Agent：家庭守门员 / 依从性教练 / 出行药师 / 采购管家
        if (isGuardianIntent(query)) {
            return guardianReply(medicines, members, plans, todayChecked, steps)
        }
        if (isCoachIntent(query)) {
            return coachReply(plans, checks, todayChecked, steps)
        }
        if (isTripIntent(query)) {
            return tripReply(query, medicines, plans, steps)
        }
        if (isShoppingIntent(query)) {
            return shoppingReply(query, medicines, plans, shoppingItems, steps)
        }

        // 0.5 药箱体检：一次给出完整巡检结论
        if (containsAny(query, listOf("体检", "巡检", "药箱检查", "检查一下药箱", "看看药箱", "整体情况", "全面检查"))) {
            return checkupReply(medicines, plans, todayChecked, steps)
        }

        // 1. 联合用药
        if (containsAny(query, listOf("一起吃", "同服", "同时吃", "一起用", "混着", "搭配"))) {
            return interactionReply(medicines, hits, steps)
        }

        // 2. 有效期
        if (containsAny(query, listOf("过期", "有效期", "到期", "临期", "失效"))) {
            return expiryReply(query, medicines, hits, steps)
        }

        // 3. 成分重复
        if (containsAny(query, listOf("重复", "买重", "同成分", "撞药", "买多了"))) {
            return duplicateReply(medicines, steps)
        }

        // 4. 今日服药与计划
        if (containsAny(query, listOf("今天", "该吃", "没吃", "打卡", "服药计划", "提醒", "按时"))) {
            return todayReply(plans, todayChecked, medicines, steps)
        }

        // 5. 指定成员
        val member = members.firstOrNull { it.name != "我" && query.contains(it.name) }
        if (member != null) return memberReply(member, medicines, steps)

        // 6. 存放位置
        if (focus != null && containsAny(query, listOf("放在哪", "放哪", "在哪", "哪里", "收纳"))) {
            return locationReply(focus, medicines, steps)
        }

        // 7. 用法用量（只读药箱记录）
        if (focus != null && containsAny(query, listOf("怎么吃", "吃多少", "用量", "剂量", "说明书", "禁忌"))) {
            return usageReply(focus, medicines, steps)
        }

        // 8. 库存盘点
        val countIntent = containsAny(query, listOf("多少种", "盘点", "总共", "库存情况", "有什么药", "药箱里")) ||
            (query.contains("药") && containsAny(query, listOf("多少", "几种", "种类")))
        if (countIntent) return inventoryReply(medicines, members, steps)

        // 9. 低库存补货
        if (containsAny(query, listOf("快用完", "不够用", "补货", "该买", "需要买什么"))) {
            return lowStockReply(medicines, steps)
        }

        // 10. 库存查询
        val ingredient = focus?.ingredient
            ?: knowledge.detectIngredient(query)
            ?: memory.ingredient
        val stockIntent = containsAny(
            query,
            listOf("有没有", "还有", "剩", "够", "库存", "要买", "需要买", "准备买", "买")
        )
        if (ingredient != null && (focus != null || stockIntent)) {
            return stockReply(query, ingredient, medicines, steps)
        }

        return fallbackReply()
    }

    // ---------------- 执行型与巡检 ----------------

    /** 把「把布洛芬设成早晚各一粒，吃 5 天」解析成可确认的计划草案。 */
    private fun planDraftReply(
        query: String,
        focus: DrugHit?,
        members: List<Member>,
        steps: MutableList<Step>
    ): Reply {
        if (focus == null) {
            steps += Step("解析服药计划", "缺少药名")
            return Reply(
                text = "要创建服药计划，先告诉我药名，比如：\n「把布洛芬设成早晚各一粒，吃 5 天」",
                steps = steps,
                suggestions = listOf("把布洛芬设成早晚各一粒，吃 5 天", "感冒灵每天两次，吃三天")
            )
        }
        val times = parseTimes(query)
        val dose = parseDose(query) ?: "1 份"
        val days = parseDays(query) ?: 7
        val member = members.firstOrNull { it.name != "我" && query.contains(it.name) }
        steps += Step("解析指令", "药名「${focus.name}」")
        steps += Step("提取剂量与频次", "$dose · 每天 ${times.size} 次")
        steps += Step("生成计划草案", "共 $days 天")

        val draft = PlanDraft(
            medicineName = focus.name,
            dose = dose,
            times = times,
            days = days,
            memberName = member?.name.orEmpty()
        )
        return Reply(
            text = "准备创建服药计划：\n" +
                "· 药品：${draft.medicineName}\n" +
                "· 剂量：$dose\n" +
                "· 时间：${times.joinToString("、")}（每天 ${times.size} 次）\n" +
                "· 疗程：$days 天" +
                (member?.let { "\n· 成员：${it.name}" } ?: "") +
                "\n\n确认后我会在本机排好提醒，不需要联网。",
            tone = Tone.NORMAL,
            steps = steps,
            suggestions = listOf("今天还有哪些药没吃", "哪些药快过期了"),
            actions = listOf(
                Action("确认创建", Action.Kind.CREATE_PLAN, plan = draft),
                Action("改一下", Action.Kind.EDIT_PLAN, plan = draft)
            )
        )
    }

    /** 药箱体检：把过期、临期、低库存、成分重复和今日打卡一次说清。 */
    private fun checkupReply(
        medicines: List<Medicine>,
        plans: List<MedicationPlan>,
        todayChecked: Set<String>,
        steps: MutableList<Step>
    ): Reply {
        val expired = medicines.filter { it.status == StockStatus.EXPIRED }
        val expiring = medicines.filter { it.status == StockStatus.EXPIRING }
        val low = medicines.filter { it.usable && it.quantity <= 5 }
        val duplicates = medicines
            .filter { it.usable && it.ingredient.isNotBlank() }
            .groupBy { it.ingredient }
            .filter { it.value.size > 1 }
        val today = DateUtils.today()
        val active = plans.filter { it.activeOn(today) }
        var total = 0
        var done = 0
        active.forEach { plan ->
            plan.times.forEach { time ->
                total += 1
                if (plan.checkKey(time) in todayChecked) done += 1
            }
        }
        steps += Step("巡检药品", "${medicines.size} 种 · 可用 ${medicines.count { it.usable }}")
        steps += Step("核对有效期", "过期 ${expired.size} · 临期 ${expiring.size}")
        steps += Step("检查库存与成分", "低库存 ${low.size} · 重复 ${duplicates.size} 组")
        steps += Step("核对今日服药", "$done/$total 已完成")

        if (medicines.isEmpty()) {
            return Reply(
                text = "药箱还是空的，先录入第一盒药，我就能开始巡检了。",
                steps = steps,
                actions = listOf(Action("录入药品", Action.Kind.ADD_MEDICINE))
            )
        }

        val issues = expired.size + expiring.size + low.size + duplicates.size
        val builder = StringBuilder()
        builder.append(
            if (issues == 0) "药箱体检完成，没发现需要处理的问题。\n"
            else "药箱体检完成，发现 $issues 项需要留意：\n"
        )
        if (expired.isNotEmpty() || expiring.isNotEmpty()) {
            val expiryParts = mutableListOf<String>()
            if (expired.isNotEmpty()) expiryParts += "已过期 ${expired.size} 种"
            if (expiring.isNotEmpty()) expiryParts += "30 天内到期 ${expiring.size} 种"
            builder.append("· 有效期：${expiryParts.joinToString("、")}，明细见下方卡片\n")
        }
        if (low.isNotEmpty()) {
            builder.append("· 余量不足：${low.take(3).joinToString("、") { "${it.name}（${it.stockLabel}）" }}\n")
        }
        if (duplicates.isNotEmpty()) {
            builder.append("· 成分重复 ${duplicates.size} 组：${duplicates.keys.take(3).joinToString("、")}\n")
        }
        if (active.isNotEmpty()) {
            builder.append("· 今日服药：${active.size} 个计划，已完成 $done/$total 次")
            if (total - done > 0) builder.append("，还有 ${total - done} 次没打卡")
            builder.append("\n")
        } else {
            builder.append("· 今天没有进行中的服药计划\n")
        }
        builder.append("\n需要我处理哪一项？可以直接点下面的按钮。")

        return Reply(
            text = builder.toString().trimEnd(),
            medicines = (expired + expiring).take(4),
            tone = when {
                expired.isNotEmpty() -> Tone.DANGER
                issues > 0 -> Tone.WARN
                else -> Tone.GOOD
            },
            steps = steps,
            suggestions = listOf("哪些药快过期了", "今天还有哪些药没吃", "该买什么药"),
            actions = buildList {
                if (expired.isNotEmpty()) add(Action("清理过期药", Action.Kind.OPEN_MEDICINES, filter = "EXPIRED"))
                if (expiring.isNotEmpty()) add(Action("查看临期药", Action.Kind.OPEN_MEDICINES, filter = "EXPIRING"))
                if (expired.isEmpty() && expiring.isEmpty()) add(Action("查看全部药品", Action.Kind.OPEN_MEDICINES))
            }
        )
    }

    private fun parseTimes(query: String): List<String> {
        val times = mutableListOf<String>()
        if (containsAny(query, listOf("早上", "早晨", "上午", "早"))) times += "08:00"
        if (containsAny(query, listOf("中午", "午餐"))) times += "12:00"
        if (containsAny(query, listOf("晚上", "晚饭", "睡前", "晚"))) times += "20:00"
        Regex("(\\d{1,2})[:：点](\\d{2})?").findAll(query).forEach { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return@forEach
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            if (hour in 0..23 && minute in 0..59) {
                times += "%02d:%02d".format(hour, minute)
            }
        }
        return times.distinct().sorted().ifEmpty { listOf("08:00") }
    }

    private fun parseDose(query: String): String? {
        val match = Regex("([一二两三四五六七八九十半1-9])\\s*(粒|片|袋|包|丸|滴|喷|毫升|ml|ML)")
            .find(query) ?: return null
        val number = when (val raw = match.groupValues[1]) {
            "一" -> "1"
            "二", "两" -> "2"
            "三" -> "3"
            "四" -> "4"
            "五" -> "5"
            "六" -> "6"
            "七" -> "7"
            "八" -> "8"
            "九" -> "9"
            else -> raw
        }
        val unit = match.groupValues[2].let { if (it.equals("ml", ignoreCase = true)) "ml" else it }
        return "$number $unit"
    }

    private fun parseDays(query: String): Int? {
        val match = Regex("(\\d{1,3}|[一二两三四五六七八九十]{1,3})\\s*天").find(query) ?: return null
        val raw = match.groupValues[1]
        return raw.toIntOrNull() ?: chineseNumber(raw)
    }

    private fun chineseNumber(raw: String): Int {
        val digits = mapOf(
            '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
        )
        return when {
            raw == "十" -> 10
            raw.contains("十") -> {
                val parts = raw.split("十")
                val tens = if (parts[0].isEmpty()) 1 else digits[parts[0][0]] ?: 1
                val ones = if (parts.size > 1 && parts[1].isNotEmpty()) digits[parts[1][0]] ?: 0 else 0
                tens * 10 + ones
            }
            else -> digits[raw.firstOrNull()] ?: 7
        }
    }

    // ---------------- 工具型回答 ----------------

    private fun expiryReply(
        query: String,
        medicines: List<Medicine>,
        hits: List<DrugHit>,
        steps: MutableList<Step>
    ): Reply {
        val focus = hits.firstOrNull()
        val scope = if (focus != null) {
            medicines.filter { it.ingredient == focus.ingredient || it.name.contains(focus.name) }
        } else {
            medicines
        }
        steps += Step("检索药品", if (focus != null) "匹配「${focus.name}」${scope.size} 条" else "全部 ${scope.size} 种")

        if (scope.isEmpty()) {
            return Reply(
                text = "药箱里没有「${focus!!.name}」的记录，暂时查不到它的有效期。\n\n" +
                    "买回来拍照入库后，我会自动帮你盯着到期时间。",
                tone = Tone.NORMAL,
                steps = steps,
                suggestions = listOf("哪些药快过期了", "家里一共有多少种药"),
                actions = listOf(Action("拍照入库", Action.Kind.ADD_MEDICINE))
            )
        }

        val expired = scope.filter { it.status == StockStatus.EXPIRED }
        val expiring = scope.filter { it.status == StockStatus.EXPIRING }
        steps += Step("核对有效期", "过期 ${expired.size} · 临期 ${expiring.size}")

        val prefix = if (query.contains("能吃") || query.contains("能用") || query.contains("还能"))
            "过期的药成分会降解，不建议继续服用。\n\n" else ""

        if (focus != null) {
            val first = scope.minByOrNull { it.daysLeft ?: Long.MAX_VALUE } ?: scope.first()
            val statusText = when (first.status) {
                StockStatus.EXPIRED -> "已经过期（${first.expiryShortLabel}），请不要再服用。"
                StockStatus.EXPIRING -> "还在有效期内，但${first.expiryShortLabel}，建议优先使用。"
                StockStatus.NORMAL -> "还在有效期内（${DateUtils.monthDay(first.expiryDate)} 到期）。"
            }
            return Reply(
                text = prefix + "「${first.name}」${first.stockLabel}，$statusText",
                medicines = listOf(first),
                tone = when (first.status) {
                    StockStatus.EXPIRED -> Tone.DANGER
                    StockStatus.EXPIRING -> Tone.WARN
                    StockStatus.NORMAL -> Tone.GOOD
                },
                steps = steps,
                suggestions = listOf("${first.name}放在哪", "哪些药快过期了"),
                actions = listOf(Action("查看药品", Action.Kind.OPEN_MEDICINE, first.id)).takeIf {
                    first.status != StockStatus.EXPIRED
                } ?: listOf(
                    Action("清理过期药", Action.Kind.OPEN_MEDICINES, filter = "EXPIRED"),
                    Action("查看药品", Action.Kind.OPEN_MEDICINE, first.id)
                )
            )
        }

        if (expired.isEmpty() && expiring.isEmpty()) {
            return Reply(
                text = prefix + "好消息：药箱里没有过期或 30 天内到期的药品，都在安全期内。",
                tone = Tone.GOOD,
                steps = steps,
                actions = listOf(Action("去药箱看看", Action.Kind.OPEN_MEDICINES))
            )
        }

        val builder = StringBuilder(prefix)
        if (expired.isNotEmpty()) {
            builder.append("有 ${expired.size} 种已经过期，建议尽快清理：\n")
            expired.forEach { builder.append("· ${it.name}（${it.expiryShortLabel}）\n") }
        }
        if (expiring.isNotEmpty()) {
            if (builder.isNotEmpty() && !builder.endsWith("\n\n")) builder.append("\n")
            builder.append("还有 ${expiring.size} 种在 30 天内到期，优先用它们：\n")
            expiring.forEach { builder.append("· ${it.name}（${it.expiryShortLabel}）\n") }
        }
        return Reply(
            text = builder.toString().trimEnd(),
            medicines = (expired + expiring).sortedBy { it.daysLeft ?: Long.MAX_VALUE },
            tone = if (expired.isNotEmpty()) Tone.DANGER else Tone.WARN,
            steps = steps,
            suggestions = listOf("该买什么药", "家里一共有多少种药"),
            actions = buildList {
                if (expired.isNotEmpty()) add(Action("查看过期药品", Action.Kind.OPEN_MEDICINES, filter = "EXPIRED"))
                if (expiring.isNotEmpty()) add(Action("查看临期药品", Action.Kind.OPEN_MEDICINES, filter = "EXPIRING"))
            }
        )
    }

    private fun duplicateReply(medicines: List<Medicine>, steps: MutableList<Step>): Reply {
        val groups = medicines
            .filter { it.usable && it.ingredient.isNotBlank() }
            .groupBy { it.ingredient }
            .filter { it.value.size > 1 }
        steps += Step("按成分分组", "${medicines.count { it.usable }} 种可用药品 → ${groups.size} 组重复")
        if (groups.isEmpty()) {
            return Reply(
                text = "目前没有发现同成分的重复药品，家里库存没有撞车。",
                tone = Tone.GOOD,
                steps = steps
            )
        }
        val builder = StringBuilder("发现有 ${groups.size} 组药品成分重复，买新的之前留意一下：\n")
        groups.forEach { (ingredient, list) ->
            builder.append("\n· 成分「$ingredient」共 ${list.size} 种\n")
            list.forEach { builder.append("   - ${it.name}（${it.stockLabel}）\n") }
        }
        return Reply(
            text = builder.toString().trimEnd(),
            medicines = groups.values.flatten(),
            tone = Tone.WARN,
            steps = steps,
            actions = listOf(Action("查看全部药品", Action.Kind.OPEN_MEDICINES))
        )
    }

    private fun interactionReply(
        medicines: List<Medicine>,
        hits: List<DrugHit>,
        steps: MutableList<Step>
    ): Reply {
        steps += Step("识别对比药品", hits.joinToString(" / ") { it.name }.ifBlank { "未识别" })
        if (hits.size < 2) {
            val only = hits.firstOrNull()?.name
            val other = if (only == "泰诺") "布洛芬" else "泰诺"
            return Reply(
                text = if (only != null) {
                    "已经找到「$only」，再告诉我要对比的另一种药，比如「${only}和${other}能一起吃吗」。"
                } else {
                    "请告诉我具体是哪两种药，比如「布洛芬和泰诺能一起吃吗」，我来帮你比对成分和类别。"
                },
                tone = Tone.NORMAL,
                steps = steps,
                suggestions = listOf("布洛芬和泰诺能一起吃吗", "感冒灵和泰诺能一起吃吗")
            )
        }

        val a = hits[0]
        val b = hits[1]
        steps += Step("比对成分", "${a.ingredient} vs ${b.ingredient}")
        val sameIngredient = a.ingredient == b.ingredient
        val sameClass = !sameIngredient &&
            a.entry?.classGroup != null &&
            a.entry.classGroup == b.entry?.classGroup
        val homeRelated = medicines.filter {
            it.usable && (it.ingredient == a.ingredient || it.ingredient == b.ingredient)
        }
        steps += Step("联查药箱", "${homeRelated.size} 种相关存货")

        if (sameIngredient) {
            return Reply(
                text = "先别一起吃。\n\n「${a.name}」和「${b.name}」的有效成分都是${a.ingredient}，" +
                    "同时服用属于重复用药，容易超过安全剂量。\n\n" +
                    "建议只保留一种，并严格按照说明书的间隔和剂量服用；拿不准时请咨询医师或药师。",
                medicines = homeRelated,
                tone = Tone.DANGER,
                steps = steps,
                suggestions = listOf("哪些药快过期了", "家里还有多少种药")
            )
        }
        if (sameClass) {
            return Reply(
                text = "需要注意同类重复。\n\n「${a.name}」（${a.ingredient}）与「${b.name}」（${b.ingredient}）" +
                    "同属「${a.entry?.classGroup}」类药物，同时服用一般没有必要，也可能增加不良反应风险。\n\n" +
                    "建议先选一种，症状不缓解再就医。",
                medicines = homeRelated,
                tone = Tone.WARN,
                steps = steps,
                suggestions = listOf("${a.name}放在哪", "哪些药快过期了")
            )
        }
        val notes = buildList {
            a.entry?.note?.takeIf { it.isNotBlank() }?.let { add("${a.ingredient}：$it") }
            b.entry?.note?.takeIf { it.isNotBlank() }?.let { add("${b.ingredient}：$it") }
        }
        return Reply(
            text = "这两种药成分不同，也没有记录显示同类重复，一般不存在叠加风险。\n\n" +
                (notes.joinToString("\n").ifBlank { "" }) +
                "\n具体是否适合同服，请以说明书为准，特殊人群或正在服其他药时请咨询医师或药师。",
            medicines = homeRelated,
            tone = Tone.GOOD,
            steps = steps
        )
    }

    private fun memberReply(
        member: Member,
        medicines: List<Medicine>,
        steps: MutableList<Step>
    ): Reply {
        val list = medicines.filter { it.memberId == member.id }
        val expired = list.filter { it.status == StockStatus.EXPIRED }
        val expiring = list.filter { it.status == StockStatus.EXPIRING }
        steps += Step("按成员检索", "${member.name} · ${list.size} 种")
        steps += Step("核对状态", "过期 ${expired.size} · 临期 ${expiring.size}")

        if (list.isEmpty()) {
            return Reply(
                text = "${member.name}名下还没有登记药品。录入时把「归属成员」选成${member.name}，之后就能单独查了。",
                tone = Tone.NORMAL,
                steps = steps,
                actions = listOf(Action("录入药品", Action.Kind.ADD_MEDICINE))
            )
        }
        val text = buildString {
            append("${member.name}名下共 ${list.size} 种药品")
            append(if (expired.isEmpty()) "，都还在有效期内。" else "，其中 ${expired.size} 种已过期。")
            if (expiring.isNotEmpty()) append("\n\n30 天内到期 ${expiring.size} 种，优先使用：\n")
            if (expiring.isNotEmpty()) expiring.forEach { append("· ${it.name}（${it.expiryShortLabel}）\n") }
            append("\n" + list.take(4).joinToString("\n") { "· ${it.name} ${it.stockLabel}" })
        }
        return Reply(
            text = text.trimEnd(),
            medicines = list,
            tone = if (expired.isNotEmpty()) Tone.WARN else Tone.NORMAL,
            steps = steps,
            actions = listOf(Action("查看全部药品", Action.Kind.OPEN_MEDICINES))
        )
    }

    private fun locationReply(
        focus: DrugHit,
        medicines: List<Medicine>,
        steps: MutableList<Step>
    ): Reply {
        val home = medicines.filter { it.ingredient == focus.ingredient || it.name.contains(focus.name) }
        steps += Step("查询存放位置", "${focus.name} · ${home.size} 条记录")
        if (home.isEmpty()) {
            return Reply(
                text = "药箱里没有「${focus.name}」的记录。先拍照入库，之后就能查到余量和位置。",
                tone = Tone.NORMAL,
                steps = steps,
                actions = listOf(Action("拍照入库", Action.Kind.ADD_MEDICINE))
            )
        }
        val located = home.firstOrNull { it.location.isNotBlank() }
        return Reply(
            text = if (located != null) {
                "「${located.name}」放在「${located.location}」，${located.stockLabel}。"
            } else {
                "「${home.first().name}」还没有记录摆放位置。\n\n可以在药品详情里补充，比如「客厅电视柜第二层」，下次就能直接查到。"
            },
            medicines = listOf(located ?: home.first()),
            tone = Tone.NORMAL,
            steps = steps,
            actions = listOf(Action("打开药品", Action.Kind.OPEN_MEDICINE, home.first().id))
        )
    }

    private fun usageReply(
        focus: DrugHit,
        medicines: List<Medicine>,
        steps: MutableList<Step>
    ): Reply {
        val home = medicines.firstOrNull { it.ingredient == focus.ingredient || it.name.contains(focus.name) }
        steps += Step("读取药品记录", home?.name ?: focus.name)
        val detail = home?.let {
            listOfNotNull(
                it.spec.takeIf { s -> s.isNotBlank() }?.let { s -> "规格 $s" },
                it.form.takeIf { s -> s.isNotBlank() }?.let { s -> "剂型 $s" },
                it.note.takeIf { s -> s.isNotBlank() }?.let { s -> "备注 $s" }
            ).joinToString("，")
        }.orEmpty()
        val note = knowledge.entryOf(focus.ingredient)?.note
        val text = buildString {
            append("「${home?.name ?: focus.name}」的药箱记录：")
            append(detail.ifBlank { "还没有填写用法用量" })
            append("。")
            if (!note.isNullOrBlank()) append("\n\n成分提示：$note")
            append("\n\n具体剂量和禁忌请以说明书为准；老人、儿童、孕妇或正在服其他药时，先问医师或药师。")
        }
        return Reply(
            text = text,
            medicines = listOfNotNull(home),
            tone = Tone.NORMAL,
            steps = steps,
            actions = listOfNotNull(home?.let { Action("打开药品", Action.Kind.OPEN_MEDICINE, it.id) })
        )
    }

    private fun inventoryReply(
        medicines: List<Medicine>,
        members: List<Member>,
        steps: MutableList<Step>
    ): Reply {
        val expired = medicines.count { it.status == StockStatus.EXPIRED }
        val expiring = medicines.count { it.status == StockStatus.EXPIRING }
        val low = medicines.filter { it.usable && it.quantity <= 5 }
        steps += Step("盘点药箱", "共 ${medicines.size} 种 · ${members.size} 位成员")
        steps += Step("分类统计", "过期 $expired · 临期 $expiring · 低库存 ${low.size}")
        if (medicines.isEmpty()) {
            return Reply(
                text = "药箱还是空的。先拍照或手动录入第一盒药，我就能开始帮你管理了。",
                tone = Tone.NORMAL,
                steps = steps,
                actions = listOf(Action("录入药品", Action.Kind.ADD_MEDICINE))
            )
        }
        val builder = StringBuilder("药箱里共 ${medicines.size} 种药品：\n")
        builder.append("· 可用 ${medicines.count { it.usable }} 种\n")
        if (expiring > 0) builder.append("· 30 天内到期 $expiring 种\n")
        if (expired > 0) builder.append("· 已过期 $expired 种\n")
        if (low.isNotEmpty()) {
            builder.append("\n余量偏少的 ${low.size} 盒放在下面，点卡片可以直接查看或修改数量。")
        }
        return Reply(
            text = builder.toString().trimEnd(),
            medicines = low.sortedBy { it.quantity }.take(4),
            tone = when {
                expired > 0 -> Tone.WARN
                expiring > 0 -> Tone.WARN
                else -> Tone.GOOD
            },
            steps = steps,
            suggestions = listOf("哪些药快过期了", "该买什么药"),
            actions = listOf(Action("查看全部药品", Action.Kind.OPEN_MEDICINES))
        )
    }

    private fun lowStockReply(medicines: List<Medicine>, steps: MutableList<Step>): Reply {
        val low = medicines.filter { it.usable && it.quantity <= 5 }.sortedBy { it.quantity }
        steps += Step("计算余量", "低于 6 份的 ${low.size} 种")
        if (low.isEmpty()) {
            return Reply(
                text = "目前没有余量告急的药品，库存都还够用。",
                tone = Tone.GOOD,
                steps = steps
            )
        }
        val text = "这 ${low.size} 种药快用完了：\n" +
            low.take(5).joinToString("\n") { "· ${it.name}（${it.stockLabel}）" } +
            "\n\n需要的话记得到货后拍照入库。"
        return Reply(
            text = text,
            medicines = low.take(5),
            tone = Tone.WARN,
            steps = steps,
            actions = listOf(Action("查看全部药品", Action.Kind.OPEN_MEDICINES))
        )
    }

    private fun stockReply(
        query: String,
        ingredient: String,
        medicines: List<Medicine>,
        steps: MutableList<Step>
    ): Reply {
        val result = guard.check(query, medicines)
        val usable = result.matched.filter { it.usable }
        steps += Step("检索成分「$ingredient」", "命中 ${result.matched.size} 条")

        val memory = Memory(
            medicine = usable.firstOrNull() ?: result.matched.firstOrNull(),
            ingredient = ingredient
        )

        if (usable.isEmpty()) {
            val expired = result.matched
            val text = if (expired.isNotEmpty()) {
                "家里有${ingredient}，但是已经过期了（${expired.first().name}，${expired.first().expiryShortLabel}），不能再用，需要重新买。"
            } else {
                "家里目前没有记录含「$ingredient」的药品。买回来之后记得拍照入库，下次就能查到了。"
            }
            return Reply(
                text = text,
                medicines = expired,
                tone = if (expired.isNotEmpty()) Tone.WARN else Tone.NORMAL,
                steps = steps,
                memory = memory,
                suggestions = listOf("准备买$ingredient，家里有吗", "哪些药快过期了"),
                actions = buildList {
                    if (expired.isNotEmpty()) add(Action("清理过期药", Action.Kind.OPEN_MEDICINES, filter = "EXPIRED"))
                    add(Action("拍照入库", Action.Kind.ADD_MEDICINE))
                }
            )
        }

        val detail = usable.joinToString("；") {
            "「${it.name}」${it.stockLabel}，${it.expiryShortLabel}"
        }
        // 用户只说了成分、家里有多个具体产品时，先追问是哪一盒
        val named = usable.filter { query.contains(it.name) }
        if (named.isEmpty() && usable.size > 1 && usable.map { it.name }.distinct().size > 1) {
            steps += Step("发现多个候选", "${usable.size} 个具体药品")
            return Reply(
                text = "家里有 ${usable.size} 种含「$ingredient」的药品，你想查哪一盒？",
                medicines = usable,
                tone = Tone.NORMAL,
                steps = steps,
                memory = memory,
                candidates = usable.map { it.name }.distinct(),
                suggestions = listOf("哪些药快过期了", "今天还有哪些药没吃")
            )
        }
        val safety = result.safetyNote?.takeIf { it.isNotBlank() }?.let { "提示：$it" }
        return Reply(
            text = "有。家里有 ${usable.size} 种含「$ingredient」的药品：\n$detail\n\n" +
                (safety ?: "余量看起来够用，先不用重复购买。"),
            medicines = usable,
            tone = Tone.GOOD,
            steps = steps,
            memory = memory,
            suggestions = listOf("${ingredient}放在哪里", "${usable.first().name}快过期了吗"),
            actions = buildList {
                usable.firstOrNull()?.let { add(Action("打开药品", Action.Kind.OPEN_MEDICINE, it.id)) }
                add(Action("查看全部药品", Action.Kind.OPEN_MEDICINES))
            }
        )
    }

    private fun todayReply(
        plans: List<MedicationPlan>,
        todayChecked: Set<String>,
        medicines: List<Medicine>,
        steps: MutableList<Step>
    ): Reply {
        val today = DateUtils.today()
        val active = plans.filter { it.activeOn(today) }
        steps += Step("读取服药计划", "${active.size} 个进行中")
        if (active.isEmpty()) {
            return Reply(
                text = "今天没有进行中的服药计划。\n\n可以在首页「今日服药」里加一个，到点会自动提醒，吃过后点一下打卡。",
                tone = Tone.NORMAL,
                steps = steps,
                suggestions = listOf("哪些药快过期了", "家里一共有多少种药"),
                actions = listOf(Action("添加服药计划", Action.Kind.ADD_PLAN))
            )
        }
        var done = 0
        var total = 0
        val lines = active.map { plan ->
            val marks = plan.times.sorted().joinToString(" / ") { time ->
                val checked = plan.checkKey(time) in todayChecked
                if (checked) done += 1
                total += 1
                if (checked) "$time ✓" else time
            }
            val dose = plan.dose.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
            "· ${plan.medicineName}$dose：$marks"
        }
        steps += Step("核对今日打卡", "$done/$total 已完成")
        val left = total - done
        return Reply(
            text = buildString {
                append("今天有 ${active.size} 个服药计划，已完成 $done/$total 次。\n")
                append(lines.joinToString("\n"))
                if (left > 0) append("\n\n还有 $left 次没打卡，吃完记得点一下。") else append("\n\n今天的药都按时吃完了。")
            },
            tone = if (left > 0) Tone.WARN else Tone.GOOD,
            steps = steps,
            suggestions = listOf("哪些药快过期了", "记住吃完点打卡"),
            actions = listOf(Action("去打卡", Action.Kind.OPEN_REMINDERS))
        )
    }

    private fun abilityReply(): Reply = Reply(
        text = "我有 13 组药箱工具，还有 4 个专职角色，全部在本机运行：\n" +
            "· 库存检索（按药名/成分/成员）\n" +
            "· 有效期核对与临期提醒\n" +
            "· 同成分查重、两药联用比对\n" +
            "· 今日服药计划与打卡核对\n" +
            "· 存放位置、用法记录、低库存与购前校验\n\n" +
            "专职角色：\n" +
            "· 家庭守门员 —— 全家用药会不会冲突、今天谁没打卡\n" +
            "· 依从性教练 —— 最近总漏哪个时段、怎么调提醒\n" +
            "· 出行药师 —— 出差几天带什么药、够不够\n" +
            "· 采购管家 —— 该补什么药、买回来一键核销\n\n" +
            "直接说一句就行，比如「全家用药安全巡检」。",
        suggestions = listOf("全家用药安全巡检", "最近我按时吃药了吗", "下周出差 3 天带什么药"),
        steps = listOf(Step("加载工具集", "13 组 + 4 个专职角色 · 本地数据"))
    )

    private fun fallbackReply(): Reply = Reply(
        text = "这句话我还没匹配到合适的药箱工具。\n\n" +
            "可以这样问：\n" +
            "· 家里还有没有布洛芬？\n" +
            "· 哪些药快过期了？\n" +
            "· 泰诺和感冒灵能一起吃吗？\n" +
            "· 今天还有哪些药没吃？",
        suggestions = listOf("哪些药快过期了", "家里一共有多少种药", "今天还有哪些药没吃"),
        steps = listOf(Step("未匹配工具", "转回能力说明"))
    )

    // ---------------- 四个专职 Agent ----------------

    private fun isGuardianIntent(query: String): Boolean =
        containsAny(
            query,
            listOf("守门员", "全家", "家里人", "家人的药", "谁没打卡", "谁没吃", "谁忘了", "跨成员", "家人用药")
        )

    private fun isCoachIntent(query: String): Boolean =
        containsAny(
            query,
            listOf("依从", "按时", "规律", "老忘", "总忘", "忘记吃", "漏服", "教练", "坚持吃", "打卡率", "吃得准", "吃药习惯")
        )

    private fun isTripIntent(query: String): Boolean =
        containsAny(query, listOf("出差", "旅行", "旅游", "出门", "回老家", "出行", "带什么药", "随身药"))

    private fun isShoppingIntent(query: String): Boolean =
        containsAny(
            query,
            listOf(
                "购物清单", "买药清单", "采购", "补货", "该买什么", "要买什么", "需要买什么",
                "买点什么", "买回来了", "买好了", "都买了", "到货了"
            )
        )

    private data class MemberReview(
        val name: String,
        val planCount: Int,
        val sameIngredient: List<String>,
        val sameClass: List<String>,
        val missed: List<String>,
        val careNote: String?
    )

    /** 家庭守门员：把全家人的在服药物放进同一个视角，查重复、同类叠加和漏服。 */
    private fun guardianReply(
        medicines: List<Medicine>,
        members: List<Member>,
        plans: List<MedicationPlan>,
        todayChecked: Set<String>,
        steps: MutableList<Step>
    ): Reply {
        val today = DateUtils.today()
        val active = plans.filter { it.activeOn(today) }
        steps += Step("加载家庭成员", "${members.size} 位 · 在服计划 ${active.size} 个")

        if (active.isEmpty()) {
            return Reply(
                text = "现在没有进行中的服药计划，守门员还没有可巡检的用药组合。\n\n" +
                    "先给家人建一个计划，我就能开始盯重复用药和漏服。",
                steps = steps,
                suggestions = listOf("今天还有哪些药没吃", "帮我做个药箱体检"),
                actions = listOf(Action("添加服药计划", Action.Kind.ADD_PLAN))
            )
        }

        val nowLabel = currentTimeLabel()
        // 先按「成员 → 在服计划」分组，兼容只有 memberName、没有 memberId 的旧计划。
        val grouped = LinkedHashMap<String, MutableList<MedicationPlan>>()
        active.forEach { plan ->
            val owner = members.firstOrNull { it.id == plan.memberId }
                ?: members.firstOrNull { plan.memberName.isNotBlank() && it.name == plan.memberName }
            val key = owner?.name ?: plan.memberName.ifBlank { "未归属成员" }
            grouped.getOrPut(key) { mutableListOf() } += plan
        }

        val reviews = grouped.map { (name, mine) ->
            val member = members.firstOrNull { it.name == name }
            val resolved = mine.map { plan ->
                plan to (
                    ToolRegistry.matchMedicine(plan, medicines)?.ingredient?.takeIf { it.isNotBlank() }
                        ?: knowledge.detectIngredient(plan.medicineName).orEmpty()
                    )
            }
            val ingredients = resolved.map { it.second }.filter { it.isNotBlank() }
            val sameIngredient = ingredients
                .groupingBy { it }.eachCount().filter { it.value > 1 }.keys.toList()
            val sameClass = ingredients
                .mapNotNull { ingredient -> knowledge.classGroupOf(ingredient)?.let { it to ingredient } }
                .groupBy({ it.first }, { it.second })
                .filter { it.value.distinct().size > 1 }
                .keys.toList()
            val missed = resolved.flatMap { (plan, _) ->
                plan.times
                    .filter { it <= nowLabel && plan.checkKey(it) !in todayChecked }
                    .map { "$it ${plan.medicineName.substringBefore(" ")}" }
            }
            val age = ageOf(member?.birthday)
            val needsCare = member?.needsCare == true
            val careNote = when {
                age != null && age < 12 -> "儿童用药，剂量需要按年龄/体重核对"
                needsCare && mine.size >= 3 -> "同时在服 ${mine.size} 种药，建议家属定期复核用法"
                needsCare && missed.isNotEmpty() -> "记性需要照顾的成员，漏服时最好由家属确认"
                else -> null
            }
            MemberReview(name, mine.size, sameIngredient, sameClass, missed, careNote)
        }

        val doubleUse = reviews.sumOf { it.sameIngredient.size + it.sameClass.size }
        val missedCount = reviews.sumOf { it.missed.size }
        steps += Step("逐人比对成分", "${reviews.size} 位成员 · 重复/同类 $doubleUse 处")
        steps += Step("核对今日打卡", "还有 $missedCount 次没打卡")

        val builder = StringBuilder("家庭守门员巡检：${reviews.size} 位成员、${active.size} 个在服计划。\n")
        reviews.forEach { review ->
            builder.append("\n${review.name}（${review.planCount} 个计划）\n")
            var flagged = false
            if (review.sameIngredient.isNotEmpty()) {
                builder.append("· 同成分重复：${review.sameIngredient.joinToString("、")}\n")
                flagged = true
            }
            if (review.sameClass.isNotEmpty()) {
                builder.append("· 同类药物叠加：${review.sameClass.joinToString("、")}\n")
                flagged = true
            }
            if (review.missed.isNotEmpty()) {
                builder.append("· 还没打卡：${review.missed.take(3).joinToString("、")}\n")
                flagged = true
            }
            review.careNote?.let {
                builder.append("· $it\n")
                flagged = true
            }
            if (!flagged) builder.append("· 没有发现用药冲突，今日打卡正常\n")
        }
        builder.append(
            if (doubleUse == 0 && missedCount == 0) {
                "\n结论：全家的用药组合没有撞车，今天的药也都按时吃了。"
            } else {
                "\n结论：先处理上面标出的项；不确定能否联用时，先问医师或药师。"
            }
        )

        return Reply(
            text = builder.toString(),
            tone = when {
                doubleUse > 0 -> Tone.DANGER
                missedCount > 0 || reviews.any { it.careNote != null } -> Tone.WARN
                else -> Tone.GOOD
            },
            steps = steps,
            suggestions = listOf("最近我按时吃药了吗", "哪些药快过期了"),
            actions = buildList {
                if (missedCount > 0) add(Action("去打卡", Action.Kind.OPEN_REMINDERS))
                add(Action("查看全部药品", Action.Kind.OPEN_MEDICINES))
            }
        )
    }

    /** 依从性教练：回放 14 天打卡，找最常漏的时段并给出调整建议。 */
    private fun coachReply(
        plans: List<MedicationPlan>,
        checks: Map<String, Set<String>>,
        todayChecked: Set<String>,
        steps: MutableList<Step>
    ): Reply {
        val today = DateUtils.today()
        val nowLabel = currentTimeLabel()
        var done = 0
        var total = 0
        // key = planId@time -> [完成次数, 漏服次数]
        val stats = LinkedHashMap<String, IntArray>()

        for (offset in 0L..13L) {
            val date = today.minusDays(offset)
            val iso = DateUtils.toIso(date)
            val checked = if (offset == 0L) todayChecked else checks[iso].orEmpty()
            plans.filter { it.activeOn(date) }.forEach { plan ->
                plan.times.forEach timeLoop@ { time ->
                    if (offset == 0L && time > nowLabel) return@timeLoop
                    val stat = stats.getOrPut("${plan.id}@$time") { IntArray(2) }
                    total += 1
                    if (plan.checkKey(time) in checked) {
                        stat[0] += 1
                        done += 1
                    } else {
                        stat[1] += 1
                    }
                }
            }
        }
        steps += Step("回放最近 14 天", "$total 次服药安排")

        if (total == 0) {
            return Reply(
                text = "最近两周没有需要打卡的服药安排，还没有可以分析的依从性数据。\n\n" +
                    "建一个长期计划后，我会按周给你复盘。",
                steps = steps,
                suggestions = listOf("今天还有哪些药没吃"),
                actions = listOf(Action("添加服药计划", Action.Kind.ADD_PLAN))
            )
        }

        val rate = done * 100 / total
        steps += Step("计算依从率", "$rate%（$done/$total）")

        val worst = plans
            .mapNotNull { plan ->
                val time = plan.times.maxByOrNull { stats["${plan.id}@$it"]?.get(1) ?: 0 } ?: return@mapNotNull null
                val missed = stats["${plan.id}@$time"]?.get(1) ?: 0
                if (missed <= 0) null else Triple(plan, time, missed)
            }
            .maxByOrNull { it.third }
        steps += Step(
            "定位漏服时段",
            worst?.let { "${it.first.memberName.ifBlank { "我" }} ${it.second}" } ?: "无集中漏服"
        )

        val perMember = plans.groupBy { it.memberName.ifBlank { "我" } }.mapNotNull { (name, list) ->
            var memberDone = 0
            var memberTotal = 0
            for (plan in list) {
                for (time in plan.times) {
                    val stat = stats["${plan.id}@$time"] ?: continue
                    memberDone += stat[0]
                    memberTotal += stat[0] + stat[1]
                }
            }
            if (memberTotal == 0) null else "$name ${memberDone * 100 / memberTotal}%"
        }

        val builder = StringBuilder("最近 14 天依从率 $rate%（$done/$total 次）。\n")
        builder.append(
            when {
                rate >= 90 -> "整体坚持得很好，基本没有断档。\n"
                rate >= 70 -> "整体还行，但有几个固定时段容易漏。\n"
                else -> "漏服偏多，建议先把提醒时间调到真正在家的时段。\n"
            }
        )
        if (worst != null) {
            val who = worst.first.memberName.ifBlank { "我" }
            val suggested = shiftTime(worst.second, 60)
            builder.append("\n最容易漏：$who · ${worst.second}「${worst.first.medicineName.substringBefore(" ")}」")
            builder.append("，14 天里漏了 ${worst.third} 次。\n")
            builder.append("建议把这剂挪到 $suggested 之后，或绑定一个固定生活节点（比如晚饭后、刷牙前）。")
        }
        if (perMember.size > 1) {
            builder.append("\n\n按成员：${perMember.joinToString(" · ")}")
        }

        return Reply(
            text = builder.toString(),
            tone = when {
                rate >= 90 -> Tone.GOOD
                rate >= 70 -> Tone.NORMAL
                else -> Tone.WARN
            },
            steps = steps,
            suggestions = listOf("今天还有哪些药没吃", "全家谁没打卡"),
            actions = listOf(
                Action("去调整提醒", Action.Kind.OPEN_REMINDERS),
                Action("今天吃什么药", Action.Kind.OPEN_REMINDERS)
            )
        )
    }

    /** 出行药师：按行程天数把在服计划换算成随身药包，并提示补货缺口。 */
    private fun tripReply(
        query: String,
        medicines: List<Medicine>,
        plans: List<MedicationPlan>,
        steps: MutableList<Step>
    ): Reply {
        val days = parseDays(query) ?: 3
        val scene = if (query.contains("出差")) "出差" else "出行"
        steps += Step("解析行程", "$scene $days 天")
        val today = DateUtils.today()
        val active = plans.filter { it.activeOn(today) }
        steps += Step("汇总在服计划", "${active.size} 个")

        if (active.isEmpty()) {
            val basics = StarterKit.groups
                .filter { it.title != "老人慢病" }
                .flatMap { it.drugs }
                .distinctBy { it.name }
                .take(5)
                .joinToString("、") { it.name }
            return Reply(
                text = "现在没有在服的长期计划，按 $days 天$scene 给你一份基础随身药包：\n\n" +
                    "· $basics\n\n" +
                    "打包提醒：\n· 用分装药盒按天分好，随身别托运\n· 有慢性病或处方药，带药盒和处方照片\n" +
                    "· 到酒店先把药放在固定位置",
                tone = Tone.NORMAL,
                steps = steps,
                suggestions = listOf("帮我做个药箱体检", "该买什么药"),
                actions = listOf(Action("查看全部药品", Action.Kind.OPEN_MEDICINES))
            )
        }

        val packs = LinkedHashMap<Long, Pair<Medicine, MutableList<MedicationPlan>>>()
        val unmatched = mutableListOf<MedicationPlan>()
        active.forEach { plan ->
            val medicine = ToolRegistry.matchMedicine(plan, medicines)
            if (medicine == null) {
                unmatched += plan
            } else {
                packs.getOrPut(medicine.id) { medicine to mutableListOf() }.second += plan
            }
        }

        val shortfall = mutableListOf<String>()
        val lines = mutableListOf<String>()
        packs.values.forEach { (medicine, planList) ->
            val perDay = planList.maxOf { ToolRegistry.dosesPerDay(it) }
            val need = perDay * days
            val note = if (medicine.quantity >= need) {
                "药箱剩 ${medicine.quantity}${medicine.unit}，够用"
            } else {
                shortfall += medicine.name
                "药箱只剩 ${medicine.quantity}${medicine.unit}，出发前补 ${need - medicine.quantity}${medicine.unit}"
            }
            val expiry = if (medicine.status != StockStatus.NORMAL) {
                "（${medicine.expiryShortLabel}，建议换一盒）"
            } else {
                ""
            }
            lines += "· ${medicine.name}：每天 $perDay 次 × $days 天 = $need${medicine.unit}，$note$expiry"
        }
        steps += Step("计算随身用量", "${packs.size} 种 · 缺口 ${shortfall.size} 种")

        val builder = StringBuilder("$scene $days 天，按在服计划理了一份随身药包：\n\n")
        builder.append(lines.joinToString("\n"))
        if (unmatched.isNotEmpty()) {
            val names = unmatched.joinToString("、") { it.medicineName }
            builder.append("\n\n· 还没入库：$names（出发前补录库存，才能算够不够）")
        }
        builder.append(
            "\n\n打包提醒：\n· 分装药盒按天分好，随身行李别托运\n· 处方药带药盒和处方照片\n" +
                "· 到酒店先把药放在固定位置，别随手塞包里"
        )

        return Reply(
            text = builder.toString(),
            tone = if (shortfall.isNotEmpty()) Tone.WARN else Tone.GOOD,
            steps = steps,
            suggestions = listOf("该买什么药", "哪些药快过期了"),
            actions = buildList {
                if (shortfall.isNotEmpty()) {
                    add(Action("缺口加入购物清单", Action.Kind.ADD_TO_SHOPPING, items = shortfall))
                }
                add(Action("查看全部药品", Action.Kind.OPEN_MEDICINES))
            }
        )
    }

    /** 采购管家：按库存余量、计划消耗速度生成清单，并支持「买回来了」核销。 */
    private fun shoppingReply(
        query: String,
        medicines: List<Medicine>,
        plans: List<MedicationPlan>,
        shoppingItems: List<String>,
        steps: MutableList<Step>
    ): Reply {
        if (containsAny(query, listOf("买回来", "买好", "买了", "到货"))) {
            if (shoppingItems.isEmpty()) {
                return Reply(
                    text = "购物清单现在是空的，没有待核销的项目。\n\n" +
                        "买回来的药直接拍照入库，我会开始盯余量和有效期。",
                    steps = steps,
                    actions = listOf(Action("拍照入库", Action.Kind.ADD_MEDICINE))
                )
            }
            steps += Step("核对购物清单", "${shoppingItems.size} 项待核销")
            return Reply(
                text = "清单里还有 ${shoppingItems.size} 项：${shoppingItems.joinToString("、")}。\n\n" +
                    "确认都买回来了？点一下我就清空清单，接着记得拍照入库补上有效期。",
                tone = Tone.GOOD,
                steps = steps,
                actions = listOf(
                    Action("确认已买回", Action.Kind.CLEAR_SHOPPING, items = shoppingItems),
                    Action("拍照入库", Action.Kind.ADD_MEDICINE)
                )
            )
        }

        val today = DateUtils.today()
        val needs = LinkedHashMap<String, String>()
        medicines.filter { it.status == StockStatus.EXPIRED }
            .forEach { needs[it.name] = "已过期，需要替换" }
        plans.filter { it.activeOn(today) }.forEach { plan ->
            val medicine = ToolRegistry.matchMedicine(plan, medicines) ?: return@forEach
            if (medicine.status == StockStatus.EXPIRED) return@forEach
            val daysLeft = ToolRegistry.stockDaysLeft(medicine, ToolRegistry.dosesPerDay(plan))
                ?: return@forEach
            if (daysLeft <= 7) needs.putIfAbsent(medicine.name, "按计划只够 $daysLeft 天")
        }
        medicines.filter { it.usable && it.quantity <= 5 }
            .sortedBy { it.quantity }
            .forEach { needs.putIfAbsent(it.name, "余量只剩 ${it.quantity}${it.unit}") }

        steps += Step(
            "扫描库存",
            "${medicines.size} 种 · 过期 ${medicines.count { it.status == StockStatus.EXPIRED }}"
        )
        steps += Step("结合计划算消耗", "缺口 ${needs.size} 项")

        if (needs.isEmpty()) {
            return Reply(
                text = "暂时没有需要补的药：在服药品余量都够用，也没有过期品。\n\n" +
                    "以后我会按计划消耗速度提前提醒你。",
                tone = Tone.GOOD,
                steps = steps,
                suggestions = listOf("帮我做个药箱体检", "哪些药快过期了"),
                actions = listOf(Action("查看全部药品", Action.Kind.OPEN_MEDICINES))
            )
        }

        val names = needs.keys.take(8).toList()
        val builder = StringBuilder()
        if (shoppingItems.isNotEmpty()) {
            builder.append("清单里已有 ${shoppingItems.size} 项：${shoppingItems.joinToString("、")}。\n\n")
        }
        builder.append("按库存和服药计划，建议补这些：\n")
        needs.entries.take(8).forEach { (name, reason) ->
            builder.append("· $name —— $reason\n")
        }
        builder.append("\n加入清单后，买回来跟我说一声「买回来了」就能核销。")
        steps += Step("生成采购清单", "${names.size} 项")

        return Reply(
            text = builder.toString(),
            tone = Tone.WARN,
            steps = steps,
            suggestions = listOf("买回来了", "家里一共有多少种药"),
            actions = listOf(
                Action("加入购物清单", Action.Kind.ADD_TO_SHOPPING, items = names),
                Action("查看全部药品", Action.Kind.OPEN_MEDICINES)
            )
        )
    }

    private fun currentTimeLabel(): String {
        val now = java.time.LocalTime.now()
        return String.format("%02d:%02d", now.hour, now.minute)
    }

    private fun shiftTime(label: String, plusMinutes: Int): String {
        val parts = label.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return label
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val total = (hour * 60 + minute + plusMinutes).coerceIn(7 * 60, 22 * 60)
        return String.format("%02d:%02d", total / 60, total % 60)
    }

    private fun ageOf(birthday: String?): Int? {
        if (birthday.isNullOrBlank()) return null
        val year = Regex("(\\d{4})").find(birthday)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: return null
        val age = java.time.LocalDate.now().year - year
        return age.takeIf { it in 0..120 }
    }

    // ---------------- 解析工具 ----------------

    private fun detectDrugs(query: String, medicines: List<Medicine>): List<DrugHit> {
        val norm = normalize(query)
        if (norm.isBlank()) return emptyList()
        val found = mutableListOf<DrugHit>()

        knowledge.all().forEach { entry ->
            // 同一个成分条目里可能同时出现两个商品名（泰诺、感冒灵），
            // 按别名逐处匹配，允许同一成分产生多个命中，才能比对重复用药。
            val mentions = mutableListOf<Pair<Int, String>>()
            entry.aliases.forEach { alias ->
                val key = normalize(alias)
                if (key.isBlank()) return@forEach
                var from = 0
                while (true) {
                    val idx = norm.indexOf(key, from)
                    if (idx < 0) break
                    mentions += idx to alias
                    from = idx + key.length
                }
            }
            val accepted = mutableListOf<Pair<Int, String>>()
            mentions
                .sortedWith(compareBy<Pair<Int, String>> { it.first }.thenByDescending { it.second.length })
                .forEach { mention ->
                    val end = mention.first + normalize(mention.second).length
                    val overlaps = accepted.any { kept ->
                        val keptEnd = kept.first + normalize(kept.second).length
                        mention.first < keptEnd && kept.first < end
                    }
                    if (!overlaps) accepted += mention
                }
            accepted.forEach { (idx, alias) ->
                found += DrugHit(alias, entry.ingredient, entry, idx)
            }
        }
        medicines.forEach { medicine ->
            val keys = listOf(medicine.name, medicine.ingredient).filter { it.isNotBlank() }
            val idx = keys.map { norm.indexOf(normalize(it)) }.filter { it >= 0 }.minOrNull()
            if (idx != null) {
                val ingredient = medicine.ingredient.ifBlank { medicine.name }
                found += DrugHit(
                    name = medicine.name,
                    ingredient = ingredient,
                    entry = knowledge.entryOf(ingredient),
                    index = idx
                )
            }
        }
        return found
            .sortedWith(compareBy<DrugHit> { it.index }.thenByDescending { it.name.length })
            // 按药名去重：两种商品名即使成分相同也要分别保留，才能比对出「重复用药」
            .distinctBy { normalize(it.name) }
            .take(2)
    }

    private fun isFollowUp(query: String): Boolean =
        containsAny(query, listOf("它", "这个", "这种", "那它", "刚才", "还有多少", "还剩", "够吗", "放哪", "怎么吃"))

    private fun containsAny(text: String, keywords: List<String>): Boolean =
        keywords.any { text.contains(it, ignoreCase = true) }

    private fun normalize(text: String): String =
        text.lowercase().replace(" ", "").replace("\n", "")
}
