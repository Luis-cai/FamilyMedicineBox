package com.family.medbox.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.family.medbox.MediBoxApp
import com.family.medbox.data.prefs.MedicationPlanStore
import com.family.medbox.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 通知上的快捷操作：已服 / 稍后提醒。
 */
class DoseActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val planId = intent.getStringExtra(DoseAlarmReceiver.EXTRA_PLAN_ID) ?: return
        val time = intent.getStringExtra(DoseAlarmReceiver.EXTRA_TIME) ?: return
        val name = intent.getStringExtra(DoseAlarmReceiver.EXTRA_NAME).orEmpty()
        val dose = intent.getStringExtra(DoseAlarmReceiver.EXTRA_DOSE).orEmpty()
        val member = intent.getStringExtra(DoseAlarmReceiver.EXTRA_MEMBER).orEmpty()

        when (intent.action) {
            ACTION_TAKEN -> {
                val date = DateUtils.toIso(DateUtils.today())
                MedicationPlanStore(context).setChecked(
                    date = date,
                    planId = planId,
                    time = time,
                    checked = true
                )
                DoseReminderScheduler.cancelFollowUp(context, planId, time)
                val app = context.applicationContext as? MediBoxApp
                if (app != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        app.container.syncManager.pushCheck(date, planId, time, true)
                        // 通知栏打卡同样广播给家庭组，子女端能看到老人已服药
                        app.container.planStore.plans.value
                            .firstOrNull { it.id == planId }
                            ?.let { app.container.syncManager.broadcastDose(it, time, true) }
                    }
                }
            }

            ACTION_SNOOZE -> {
                DoseReminderScheduler.scheduleFollowUp(
                    context = context,
                    planId = planId,
                    time = time,
                    name = name,
                    dose = dose,
                    memberName = member,
                    delayMinutes = 15
                )
            }
        }

        NotificationHelper.cancelDose(context, planId, time)
    }

    companion object {
        const val ACTION_TAKEN = "com.family.medbox.DOSE_TAKEN"
        const val ACTION_SNOOZE = "com.family.medbox.DOSE_SNOOZE"
    }
}
