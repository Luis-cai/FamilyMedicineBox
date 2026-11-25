package com.family.medbox.data.model

import com.family.medbox.util.DateUtils
import java.time.LocalDate

/**
 * 一条服药计划：药名 + 每日时间点 + 疗程天数。
 * 存储在 SharedPreferences，不依赖数据库版本升级。
 */
data class MedicationPlan(
    val id: String,
    val medicineName: String,
    val dose: String = "1 次用量",
    val memberId: Long? = null,
    val memberName: String = "",
    val times: List<String> = listOf("08:00"),
    val startDate: String = DateUtils.toIso(DateUtils.today()),
    val days: Int = 7
) {
    fun checkKey(time: String): String = "$id@$time"

    val timeLabel: String get() = times.sorted().joinToString(" / ")

    fun activeOn(date: LocalDate): Boolean {
        val start = DateUtils.parseIso(startDate) ?: return false
        return !date.isBefore(start) && date.isBefore(start.plusDays(days.toLong()))
    }

    fun lastDate(): LocalDate? =
        DateUtils.parseIso(startDate)?.plusDays(days.toLong() - 1)
}

/** 打卡进度：今日与最近 7 天的完成情况 */
object DoseTracker {

    /**
     * 某一天的计划完成情况。[skipFuture] 为 true 时忽略还没到点的时间
     * （今天统计时用，避免早上就显示未完成）。
     */
    fun dailyProgress(
        plans: List<MedicationPlan>,
        checks: Map<String, Set<String>>,
        date: LocalDate,
        skipFuture: Boolean = false
    ): Pair<Int, Int> {
        val checked = checks[DateUtils.toIso(date)].orEmpty()
        val nowLabel = if (skipFuture) currentTimeLabel() else null
        var done = 0
        var total = 0
        plans.filter { it.activeOn(date) }.forEach { plan ->
            plan.times.forEach { time ->
                if (nowLabel != null && time > nowLabel) return@forEach
                total += 1
                if (plan.checkKey(time) in checked) done += 1
            }
        }
        return done to total
    }

    /**
     * 本周依从率：统计最近 7 天中已经到点的时间点，
     * 今天只算已经过去的时间，避免早上就显示没完成。
     */
    fun weekProgress(
        plans: List<MedicationPlan>,
        checks: Map<String, Set<String>>
    ): Pair<Int, Int> {
        if (plans.isEmpty()) return 0 to 0
        val today = DateUtils.today()
        var done = 0
        var total = 0
        for (offset in 0..6) {
            val (dayDone, dayTotal) = dailyProgress(
                plans = plans,
                checks = checks,
                date = today.minusDays(offset.toLong()),
                skipFuture = offset == 0
            )
            done += dayDone
            total += dayTotal
        }
        return done to total
    }

    fun percent(done: Int, total: Int): Int =
        if (total <= 0) 0 else (done * 100 / total).coerceIn(0, 100)

    private fun currentTimeLabel(): String {
        val now = java.time.LocalTime.now()
        return String.format("%02d:%02d", now.hour, now.minute)
    }
}
