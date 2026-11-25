package com.family.medbox.domain

import com.family.medbox.data.model.Medicine
import com.family.medbox.data.model.MedicationPlan
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

enum class SuggestionSeverity { ALERT, WARNING, INFO }

/** 建议卡上的可执行动作，由界面层翻译成点击行为。 */
sealed interface AgentAction {
    data class TakeDose(val plan: MedicationPlan, val time: String) : AgentAction
    data class OpenMedicine(val medicineId: Long) : AgentAction
    object OpenReminders : AgentAction
}

data class ProactiveSuggestion(
    val id: String,
    val event: SceneEvent,
    val severity: SuggestionSeverity,
    val title: String,
    val detail: String,
    val actionLabel: String,
    val action: AgentAction
)

/**
 * 主动智能：把「用药计划 + 今日打卡 + 库存 + 当前时间」放在一起推理，
 * 输出此刻真正需要家人动手的建议。纯函数，方便测试和复用。
 */
object ProactiveEngine {

    /** 超过这个时间还没打卡，视为漏服 */
    private const val MISSED_AFTER_MINUTES = 30L

    /** 到点前多久开始提示 */
    private const val DUE_AHEAD_MINUTES = 30L

    /** 库存不足几天的量就提醒补药 */
    private const val LOW_STOCK_DAYS = 2

    fun suggestions(
        medicines: List<Medicine>,
        plans: List<MedicationPlan>,
        todayChecked: Set<String>,
        now: LocalDateTime = LocalDateTime.now(),
        limit: Int = 3
    ): List<ProactiveSuggestion> {
        if (plans.isEmpty()) return emptyList()

        val today = now.toLocalDate()
        val nowTime = now.toLocalTime()
        val activePlans = plans.filter { it.activeOn(today) }
        val result = mutableListOf<ProactiveSuggestion>()

        // 1. 漏服 / 即将到点：时间维度上最要紧的事
        activePlans.forEach { plan ->
            plan.times.sorted().forEach { time ->
                val at = parseTime(time) ?: return@forEach
                if (plan.checkKey(time) in todayChecked) return@forEach
                val minutes = Duration.between(at, nowTime).toMinutes()
                when {
                    minutes >= MISSED_AFTER_MINUTES ->
                        result += missedDose(plan, time, minutes)
                    minutes >= -DUE_AHEAD_MINUTES ->
                        result += doseDueSoon(plan, time, minutes)
                }
            }
        }

        // 2. 库存撑不到几天：结合计划消耗速度算，而不是只看剩余数量
        val plannedMedicines = mutableSetOf<Long>()
        activePlans.forEach { plan ->
            val medicine = ToolRegistry.matchMedicine(plan, medicines) ?: return@forEach
            if (!plannedMedicines.add(medicine.id)) return@forEach
            val perDay = ToolRegistry.dosesPerDay(plan)
            val daysLeft = ToolRegistry.stockDaysLeft(medicine, perDay) ?: return@forEach
            if (daysLeft <= LOW_STOCK_DAYS) {
                result += stockRunningOut(plan, medicine, perDay, daysLeft)
            }
        }

        // 3. 正在吃、又快过期的药：优先吃掉这一盒
        ToolRegistry.expiringInUse(activePlans, medicines) { true }.forEach { (medicine, plan) ->
            result += expiringInUse(plan, medicine)
        }

        return result
            .sortedBy { severityRank(it.severity) }
            .take(limit)
    }

    private fun missedDose(plan: MedicationPlan, time: String, minutes: Long): ProactiveSuggestion {
        val who = plan.memberName.ifBlank { "家人" }
        val urgent = minutes >= 120
        return ProactiveSuggestion(
            id = "missed-${plan.id}-$time",
            event = SceneEvent(
                type = SceneEventType.DOSE_MISSED,
                actorName = who,
                planId = plan.id,
                time = time
            ),
            severity = if (urgent) SuggestionSeverity.ALERT else SuggestionSeverity.WARNING,
            title = "$who $time 的药还没打卡",
            detail = "已经过去 ${humanize(minutes)}。如果已经吃过，点一下补记；还没吃的话，现在补上。",
            actionLabel = "已吃过",
            action = AgentAction.TakeDose(plan, time)
        )
    }

    private fun doseDueSoon(plan: MedicationPlan, time: String, minutes: Long): ProactiveSuggestion {
        val who = plan.memberName.ifBlank { "家人" }
        val detail = if (minutes > 0) {
            "已经到点 $minutes 分钟，现在服用正合适。"
        } else {
            "还有 ${-minutes} 分钟到点，可以先准备好温水。"
        }
        return ProactiveSuggestion(
            id = "due-${plan.id}-$time",
            event = SceneEvent(
                type = SceneEventType.DOSE_DUE,
                actorName = who,
                planId = plan.id,
                time = time
            ),
            severity = SuggestionSeverity.INFO,
            title = "$who $time 该吃「${plan.medicineName}」",
            detail = detail,
            actionLabel = "打开提醒",
            action = AgentAction.OpenReminders
        )
    }

    private fun stockRunningOut(
        plan: MedicationPlan,
        medicine: Medicine,
        perDay: Int,
        daysLeft: Int
    ): ProactiveSuggestion {
        val who = plan.memberName.ifBlank { "家人" }
        val title = if (daysLeft <= 0) {
            "「${medicine.name}」今天就用完"
        } else {
            "「${medicine.name}」只够吃 $daysLeft 天"
        }
        return ProactiveSuggestion(
            id = "stock-${medicine.id}",
            event = SceneEvent(
                type = SceneEventType.STOCK_RUNNING_OUT,
                actorName = who,
                planId = plan.id,
                medicineId = medicine.id
            ),
            severity = if (daysLeft <= 1) SuggestionSeverity.WARNING else SuggestionSeverity.INFO,
            title = title,
            detail = "$who 每天 $perDay 次，库存 ${medicine.stockLabel}。趁早补一盒，别断药。",
            actionLabel = "查看药品",
            action = AgentAction.OpenMedicine(medicine.id)
        )
    }

    private fun expiringInUse(plan: MedicationPlan, medicine: Medicine): ProactiveSuggestion {
        val who = plan.memberName.ifBlank { "家人" }
        return ProactiveSuggestion(
            id = "expiring-${medicine.id}",
            event = SceneEvent(
                type = SceneEventType.EXPIRING_SOON,
                actorName = who,
                planId = plan.id,
                medicineId = medicine.id
            ),
            severity = SuggestionSeverity.INFO,
            title = "「${medicine.name}」${medicine.expiryShortLabel}",
            detail = "$who 正在服用这盒，建议先把它吃完，再用新的。",
            actionLabel = "查看药品",
            action = AgentAction.OpenMedicine(medicine.id)
        )
    }

    private fun parseTime(label: String): LocalTime? = runCatching {
        LocalTime.parse(label.trim())
    }.getOrNull()

    private fun humanize(minutes: Long): String =
        if (minutes < 60L) "$minutes 分钟" else "${minutes / 60} 小时 ${minutes % 60} 分钟"

    private fun severityRank(severity: SuggestionSeverity): Int = when (severity) {
        SuggestionSeverity.ALERT -> 0
        SuggestionSeverity.WARNING -> 1
        SuggestionSeverity.INFO -> 2
    }
}
