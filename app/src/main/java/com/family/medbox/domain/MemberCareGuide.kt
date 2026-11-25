package com.family.medbox.domain

import com.family.medbox.data.model.MedicationPlan
import com.family.medbox.data.model.Medicine
import com.family.medbox.data.model.Member
import com.family.medbox.data.model.StockStatus
import com.family.medbox.data.prefs.FollowUp
import com.family.medbox.data.prefs.OpenedShelfLife
import com.family.medbox.util.DateUtils

enum class CareTone { NORMAL, WARN, DANGER }

/** 一条照护建议：一句话结论 + 具体怎么做。 */
data class CareTip(
    val title: String,
    val detail: String,
    val tone: CareTone = CareTone.NORMAL
)

data class CareGuide(
    val mustDo: List<CareTip>,
    val avoid: List<CareTip>,
    val watch: List<CareTip>
) {
    val total: Int get() = mustDo.size + avoid.size + watch.size
}

/**
 * 按某位成员名下的药品，生成「该做什么 / 不该做什么 / 需要注意」三组建议。
 * 全部来自本机的药品知识库、饮食与相互作用规则，只作提醒，不替代医嘱。
 */
object MemberCareGuide {

    fun build(
        member: Member,
        medicines: List<Medicine>,
        plans: List<MedicationPlan>,
        checks: Map<String, Set<String>>,
        opened: Map<Long, String>,
        followUps: List<FollowUp>,
        knowledge: DrugKnowledge
    ): CareGuide {
        val today = DateUtils.today()
        val todayIso = DateUtils.toIso(today)
        val own = medicines.filter { it.memberId == member.id }
        val activePlans = plans.filter { it.memberId == member.id && it.activeOn(today) }
        val checked = checks[todayIso].orEmpty()

        val slots = activePlans
            .flatMap { plan -> plan.times.map { plan to it } }
            .sortedBy { it.second }
        val total = slots.size
        val done = slots.count { (plan, time) -> plan.checkKey(time) in checked }
        val next = slots.firstOrNull { (plan, time) -> plan.checkKey(time) !in checked }

        val text = own.joinToString(" ") {
            listOf(it.name, it.ingredient, it.form).joinToString(" ")
        }.lowercase()
        fun has(vararg keys: String): Boolean = keys.any { text.contains(it) }
        val oral = own.filter { medicine ->
            val form = medicine.form
            listOf("喷雾", "喷剂", "滴鼻", "滴眼", "滴耳", "外用", "软膏", "乳膏", "贴剂", "栓", "洗剂")
                .none { form.contains(it) }
        }

        val mustDo = mutableListOf<CareTip>()
        val avoid = mutableListOf<CareTip>()
        val watch = mutableListOf<CareTip>()

        // ① 今天到底要做什么
        when {
            own.isEmpty() -> mustDo += CareTip(
                "先把药品分到${member.name}名下",
                "录入药品时选择成员，或在药品详情里改归属。分好之后，这里的提醒才只针对他一个人。"
            )

            total == 0 -> mustDo += CareTip(
                "还没有今天的服药计划",
                "名下已有 ${own.size} 种药，但没有设置服用时间。到「药箱 · 用药提醒」为这些药加上时间点，App 才会按时提醒。"
            )

            else -> {
                val nextText = next?.let { (plan, time) ->
                    "下一次是 $time 的 ${plan.medicineName}" +
                        if (plan.dose.isNotBlank()) "（${plan.dose}）" else ""
                } ?: "今天的 $total 次已经全部完成"
                mustDo += CareTip(
                    "把今天的 $total 次按时吃完",
                    "已完成 $done 次，还差 ${total - done} 次。$nextText。吃完记得打卡，家人才看得到进度。"
                )
            }
        }

        // ② 按病种/药物的日常动作
        if (has("地平", "普利", "沙坦", "洛尔", "降压")) {
            mustDo += CareTip(
                "每天固定时间量血压",
                "早起后、睡前各测一次，记到「指标日记」。复诊时把曲线给医生看，比只说“还行”有用得多。"
            )
            watch += CareTip(
                "起身慢一点",
                "降压药可能引起体位性低血压。久坐、久蹲后先坐一会儿再站起来，头晕时扶稳、别急着走。"
            )
        }
        if (has("二甲双胍", "格列", "胰岛素", "达格列净", "阿卡波糖", "西格列汀", "降糖")) {
            mustDo += CareTip(
                "按时吃饭，随身带糖",
                "降糖药要跟着饭走，不能因为没胃口就空腹吃药。出门带几颗糖或一小盒果汁，心慌、手抖、出冷汗时先吃糖再测血糖。"
            )
            watch += CareTip(
                "注意低血糖",
                "头晕、乏力、出冷汗、心慌都可能是低血糖；夜里出汗、做噩梦也要留意，反复出现要告诉医生。"
            )
        }
        if (has("华法林", "利伐沙班", "达比加群", "阿哌沙班", "氯吡格雷")) {
            mustDo += CareTip(
                "按医生约定的时间复查",
                "抗凝 / 抗血小板药需要定期看凝血和血常规，不要因为“感觉很好”就跳过复查。"
            )
            watch += CareTip(
                "留意出血迹象",
                "牙龈总出血、大便发黑、皮肤大片淤青、伤口很难止血，都要尽快就医。"
            )
        }
        if (has("阿仑膦酸钠", "利塞膦酸钠", "唑来膦酸")) {
            mustDo += CareTip(
                "空腹、大杯白水、别马上躺下",
                "起床后用 200ml 以上白水送服，至少 30 分钟后再吃早饭、喝牛奶或补钙；服后保持坐立，别立刻卧床。"
            )
        }
        if (has("左甲状腺素", "优甲乐", "雷替斯")) {
            mustDo += CareTip(
                "早餐前空腹吃",
                "用白水送服，30–60 分钟后再吃早餐；和钙片、铁剂、豆浆、牛奶至少隔开 4 小时。"
            )
        }
        if (has("硫酸亚铁", "琥珀酸亚铁", "富马酸亚铁", "多糖铁")) {
            mustDo += CareTip(
                "用温水送服铁剂",
                "和茶、咖啡、牛奶隔开 2 小时。大便变黑是铁剂的正常现象，不用紧张。"
            )
        }
        if (has("阿莫西林", "头孢", "阿奇霉素", "左氧氟沙星", "克拉霉素", "甲硝唑")) {
            mustDo += CareTip(
                "把疗程吃完",
                "症状好转也别提前停，否则容易反复或产生耐药；出现皮疹、严重腹泻要及时联系医生。"
            )
        }

        // ③ 效期与开封
        val expiring = own.filter { it.status == StockStatus.EXPIRING }
        if (expiring.isNotEmpty()) {
            mustDo += CareTip(
                "先用临期的 ${expiring.size} 种药",
                expiring.joinToString("；") { "${it.name}（${it.expiryShortLabel}）" } +
                    "。同类药先吃快到期的，避免放着放到过期。"
            )
        }
        val expired = own.filter { it.status == StockStatus.EXPIRED }
        if (expired.isNotEmpty()) {
            avoid += CareTip(
                "这 ${expired.size} 种药已经过期，别再吃",
                expired.joinToString("、") { it.name } +
                    "。过期药效会下降、成分可能变质，请单独收好并按社区药品回收处理。",
                CareTone.DANGER
            )
        }
        own.forEach { medicine ->
            val openedIso = opened[medicine.id] ?: return@forEach
            val left = OpenedShelfLife.daysLeft(medicine, openedIso) ?: return@forEach
            if (left >= 0) {
                mustDo += CareTip(
                    "${medicine.name} 开封后还剩 $left 天",
                    "开封日期 $openedIso。眼药水、糖浆、胰岛素这类药开封后不再按整盒有效期算，到期就换新的。"
                )
            }
        }

        // ④ 复诊 / 续方
        followUps
            .filter { it.memberId == member.id }
            .minByOrNull { it.visitIso.ifBlank { "9999-12-31" } }
            ?.let { followUp ->
                val days = followUp.daysToVisit
                val whenText = when {
                    days == null -> "复诊日期还没填"
                    days < 0L -> "复诊已过 ${-days} 天"
                    days == 0L -> "今天复诊"
                    days == 1L -> "明天复诊"
                    else -> "$days 天后复诊"
                }
                val place = listOf(followUp.hospital, followUp.department)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                val prescription = followUp.daysToPrescription?.let { left ->
                    val leftText = if (left < 0L) "已过 ${-left} 天" else "还有 $left 天"
                    "处方可用到 ${DateUtils.monthDay(followUp.prescriptionIso)}（$leftText）"
                }
                mustDo += CareTip(
                    whenText,
                    listOfNotNull(
                        place.takeIf { it.isNotBlank() },
                        prescription,
                        "去之前整理好正在吃的药和最近的指标记录。"
                    ).joinToString("，")
                )
            }

        // ⑤ 饮食禁忌：能避开的放「不该做」，要隔开的也是，需注意的放「注意」
        val foodMatches = FoodRules.matched(own)
        foodMatches.forEach { (rule, hits) ->
            val detail = rule.detail + "（涉及：" + hits.joinToString("、") { it.name } + "）"
            when (rule.level) {
                FoodRule.Level.AVOID -> avoid += CareTip(rule.title, detail, CareTone.DANGER)
                FoodRule.Level.SPACING -> avoid += CareTip(rule.title, detail, CareTone.WARN)
                FoodRule.Level.CAUTION -> watch += CareTip(rule.title, detail, CareTone.WARN)
            }
        }

        // ⑥ 药物相互作用
        val spacingCovered = foodMatches.any { (rule, _) -> rule.title.contains("蒙脱石") }
        InteractionRules.check(own).forEach { warning ->
            // 蒙脱石「与其他口服药间隔」在饮食规则里已经提示过，不再重复一条
            if (spacingCovered && warning.title.contains("间隔")) return@forEach
            val tip = CareTip(
                title = warning.title,
                detail = warning.detail + "（涉及：" + warning.medicineNames.joinToString("、") + "）",
                tone = if (warning.severity == InteractionSeverity.HIGH) CareTone.DANGER else CareTone.WARN
            )
            if (warning.severity == InteractionSeverity.HIGH) avoid += tip else watch += tip
        }

        if (oral.isNotEmpty() && avoid.none { it.title.contains("酒") }) {
            avoid += CareTip(
                "不要用酒、浓茶或果汁送服",
                "用温白开水送服。服药期间尽量不饮酒，酒精会放大镇静、降压、降糖等作用，也刺激胃黏膜。",
                CareTone.WARN
            )
        }
        if (has("地平", "普利", "沙坦", "洛尔", "二甲双胍", "格列", "胰岛素", "他汀", "华法林")) {
            avoid += CareTip(
                "不要自己加量或停药",
                "指标正常说明药在起作用，突然停药可能反弹。要调整剂量，先和医生说。",
                CareTone.WARN
            )
        }
        if (member.needsCare) {
            avoid += CareTip(
                "不要凭记忆补服",
                "想起漏服时，如果已经接近下一次服药，就跳过这一次，千万别一次吃两倍。",
                CareTone.DANGER
            )
        }

        // ⑦ 药品知识库里的注意事项
        val notes = mutableListOf<CareTip>()
        own.distinctBy { it.ingredient.ifBlank { it.name } }.forEach { medicine ->
            val entry = knowledge.bestMatch("${medicine.name} ${medicine.ingredient}") ?: return@forEach
            if (entry.note.isBlank()) return@forEach
            if (notes.any { it.title.endsWith("（${entry.ingredient}）") }) return@forEach
            // 上面已经给过「要隔开」的提醒时，不再重复知识库里同样的说明
            val duplicated = (avoid + watch).any { tip ->
                tip.detail.contains(entry.ingredient) &&
                    (tip.detail.contains("间隔") || tip.detail.contains("隔开"))
            }
            if (duplicated) return@forEach
            notes += CareTip("${medicine.name}（${entry.ingredient}）", entry.note)
        }
        watch += notes.take(6)

        if (oral.isNotEmpty()) {
            watch += CareTip(
                "漏服怎么处理",
                "想起时如果离下一次还很远，可以补服；如果已经接近下一次，跳过这一次，不要双倍补服。拿不准就问医生或药师。"
            )
        }
        if (has("他汀")) {
            watch += CareTip(
                "肌肉酸痛要报告",
                "他汀可能引起肌肉酸痛、乏力；如果出现明显肌痛或尿色像酱油，尽快就医查肌酶。"
            )
        }
        if (has("胰岛素")) {
            watch += CareTip(
                "胰岛素的保存",
                "未开封的放冰箱冷藏（2–8℃，别贴冷冻室）；开封后常温存放，避免暴晒和高温，超过 28 天换新的。"
            )
        }
        if (has("益生菌", "双歧杆菌", "妈咪爱")) {
            watch += CareTip(
                "活菌药的保存",
                "多数益生菌需要冷藏，别用热水送服，也不要和抗生素同时吃。"
            )
        }
        if (has("滴眼液", "眼药水", "眼膏")) {
            watch += CareTip(
                "眼药水的开封效期",
                "开封后 28 天用完，瓶口别碰到眼睛和睫毛；不同眼药水之间间隔 5 分钟以上。"
            )
        }
        if (member.needsCare) {
            watch += CareTip(
                "让家人帮一把",
                "用分药盒提前分好一周的药，吃完在 App 里打卡；出现头晕、乏力、皮疹，第一时间告诉家人。"
            )
        }

        return CareGuide(mustDo = mustDo, avoid = avoid, watch = watch)
    }
}
