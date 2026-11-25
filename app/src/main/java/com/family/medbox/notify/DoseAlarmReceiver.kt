package com.family.medbox.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.family.medbox.MediBoxApp
import com.family.medbox.data.model.MedicationPlan
import com.family.medbox.data.prefs.MedicationPlanStore
import com.family.medbox.util.DateUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 到点提醒服药；过点仍未打卡会再补提醒：
 * 普通成员 30 分钟后补一次，需要照护的成员 15、30 分钟后各补一次。
 */
class DoseAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val planId = intent.getStringExtra(EXTRA_PLAN_ID) ?: return
        val time = intent.getStringExtra(EXTRA_TIME) ?: return
        val name = intent.getStringExtra(EXTRA_NAME) ?: "服药提醒"
        val dose = intent.getStringExtra(EXTRA_DOSE).orEmpty()
        val member = intent.getStringExtra(EXTRA_MEMBER).orEmpty()
        val followUp = intent.getBooleanExtra(EXTRA_FOLLOW_UP, false)
        val round = intent.getIntExtra(EXTRA_ROUND, 0)

        val store = MedicationPlanStore(context)
        if (!store.remindersEnabled.value) return
        if (store.isChecked(DateUtils.toIso(DateUtils.today()), planId, time)) return

        val plan = store.plans.value.firstOrNull { it.id == planId }
        // 老人或记性不好的成员：提醒更密，文案与按钮更直白。
        val care = (context.applicationContext as? MediBoxApp)
            ?.container?.repository?.members?.value
            ?.firstOrNull { it.id == plan?.memberId }?.needsCare == true

        NotificationHelper.notifyDose(
            context = context,
            planId = planId,
            time = time,
            medicineName = name,
            dose = dose,
            memberName = member,
            missed = followUp,
            elder = care
        )

        val nextRound = round + 1
        if (nextRound <= if (care) CARE_ROUNDS else 1) {
            DoseReminderScheduler.scheduleFollowUp(
                context = context,
                planId = planId,
                time = time,
                name = name,
                dose = dose,
                memberName = member,
                delayMinutes = if (care) 15 else NORMAL_FOLLOW_UP_MINUTES,
                round = nextRound
            )
        }

        if (plan != null) DoseReminderScheduler.schedulePlan(context, plan)
    }

    companion object {
        const val ACTION = "com.family.medbox.DOSE_REMINDER"
        const val EXTRA_PLAN_ID = "plan_id"
        const val EXTRA_TIME = "dose_time"
        const val EXTRA_NAME = "dose_name"
        const val EXTRA_DOSE = "dose_amount"
        const val EXTRA_MEMBER = "dose_member"
        const val EXTRA_FOLLOW_UP = "dose_follow_up"
        const val EXTRA_ROUND = "dose_round"

        /** 需要照护的成员：到点后再补 2 次（15、30 分钟）。 */
        private const val CARE_ROUNDS = 2
        private const val NORMAL_FOLLOW_UP_MINUTES = 30L
    }
}

/**
 * 本地闹钟调度：每条计划的每个时间点各自排一个闹钟，
 * 只在本机，不需要网络；应用启动时会整体重排一次。
 */
object DoseReminderScheduler {

    private const val DEFAULT_FOLLOW_UP_MINUTES = 30L

    fun rescheduleAll(context: Context, plans: List<MedicationPlan>) {
        plans.forEach { schedulePlan(context, it) }
    }

    fun schedulePlan(context: Context, plan: MedicationPlan) {
        plan.times.forEach { time ->
            val trigger = nextTrigger(plan, time) ?: return@forEach
            val intent = Intent(context, DoseAlarmReceiver::class.java).apply {
                action = "${DoseAlarmReceiver.ACTION}.${plan.id}.$time"
                putExtra(DoseAlarmReceiver.EXTRA_PLAN_ID, plan.id)
                putExtra(DoseAlarmReceiver.EXTRA_TIME, time)
                putExtra(DoseAlarmReceiver.EXTRA_NAME, plan.medicineName)
                putExtra(DoseAlarmReceiver.EXTRA_DOSE, plan.dose)
                putExtra(DoseAlarmReceiver.EXTRA_MEMBER, plan.memberName)
            }
            val pending = PendingIntent.getBroadcast(
                context,
                requestCode(plan.id, time),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setAlarm(context, trigger, pending)
        }
    }

    /** 漏服补提醒：delayMinutes 后再触发一次，只在还没打卡时提醒。 */
    fun scheduleFollowUp(
        context: Context,
        planId: String,
        time: String,
        name: String,
        dose: String,
        memberName: String = "",
        delayMinutes: Long = DEFAULT_FOLLOW_UP_MINUTES,
        round: Int = 1
    ) {
        val intent = followUpIntent(context, planId, time, name, dose, memberName, round)
        val pending = PendingIntent.getBroadcast(
            context,
            followUpRequestCode(planId, time),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAlarm(context, System.currentTimeMillis() + delayMinutes * 60_000L, pending)
    }

    fun cancelFollowUp(context: Context, planId: String, time: String) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            followUpRequestCode(planId, time),
            followUpIntent(context, planId, time, "", "", "", round = 1),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.cancel(pending)
        pending.cancel()
    }

    fun cancelPlan(context: Context, plan: MedicationPlan) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        plan.times.forEach { time ->
            val pending = PendingIntent.getBroadcast(
                context,
                requestCode(plan.id, time),
                Intent(context, DoseAlarmReceiver::class.java).apply {
                    action = "${DoseAlarmReceiver.ACTION}.${plan.id}.$time"
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            manager.cancel(pending)
            pending.cancel()
            cancelFollowUp(context, plan.id, time)
        }
    }

    private fun followUpIntent(
        context: Context,
        planId: String,
        time: String,
        name: String,
        dose: String,
        memberName: String,
        round: Int
    ): Intent = Intent(context, DoseAlarmReceiver::class.java).apply {
        action = "${DoseAlarmReceiver.ACTION}.${planId}.$time.follow"
        putExtra(DoseAlarmReceiver.EXTRA_PLAN_ID, planId)
        putExtra(DoseAlarmReceiver.EXTRA_TIME, time)
        putExtra(DoseAlarmReceiver.EXTRA_NAME, name)
        putExtra(DoseAlarmReceiver.EXTRA_DOSE, dose)
        putExtra(DoseAlarmReceiver.EXTRA_MEMBER, memberName)
        putExtra(DoseAlarmReceiver.EXTRA_FOLLOW_UP, true)
        putExtra(DoseAlarmReceiver.EXTRA_ROUND, round)
    }

    private fun setAlarm(context: Context, triggerAt: Long, pending: PendingIntent) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager.canScheduleExactAlarms()
        runCatching {
            if (canExact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }
    }

    private fun requestCode(planId: String, time: String): Int =
        (planId.hashCode() * 31 + time.hashCode()) and 0x0FFFFFFF

    private fun followUpRequestCode(planId: String, time: String): Int =
        (("follow" + planId).hashCode() * 31 + time.hashCode()) and 0x0FFFFFFF

    private fun nextTrigger(plan: MedicationPlan, time: String): Long? {
        if (plan.days <= 0) return null
        val start = DateUtils.parseIso(plan.startDate) ?: return null
        val endExclusive = start.plusDays(plan.days.toLong())
        val localTime = runCatching { LocalTime.parse(time) }.getOrNull() ?: return null
        val now = LocalDateTime.now()

        var date: LocalDate = if (now.toLocalDate().isAfter(start)) now.toLocalDate() else start
        while (date.isBefore(endExclusive)) {
            val candidate = date.atTime(localTime)
            if (candidate.isAfter(now)) {
                return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            date = date.plusDays(1)
        }
        return null
    }
}
