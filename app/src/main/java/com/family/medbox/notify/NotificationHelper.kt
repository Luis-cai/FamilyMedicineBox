package com.family.medbox.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.family.medbox.MainActivity
import com.family.medbox.R
import com.family.medbox.data.db.MediBoxDbHelper
import com.family.medbox.data.model.StockStatus
import com.family.medbox.domain.InsightEngine

/**
 * 主动提醒：药箱是"记着"的，不需要用户主动打开 App 才发现过期。
 */
object NotificationHelper {

    private const val CHANNEL_ID = "medibox_expiry"
    private const val CHANNEL_DOSE = "medibox_dose"
    private const val CHANNEL_FAMILY = "medibox_family"
    private const val NOTIFICATION_ID = 1001
    private const val REMOTE_BASE_ID = 20_000

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "药品过期提醒",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "提醒家庭药箱中临近过期和已经过期的药品"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun notifyFromDb(context: Context) {
        val medicines = runCatching { MediBoxDbHelper(context).loadMedicines() }.getOrDefault(emptyList())
        if (medicines.isEmpty()) return

        val expired = medicines.filter { it.status == StockStatus.EXPIRED }
        val expiring = medicines.filter { it.status == StockStatus.EXPIRING }
        if (expired.isEmpty() && expiring.isEmpty()) return

        val title = when {
            expired.isNotEmpty() -> "${expired.size} 种药品已过期，记得清理"
            else -> "${expiring.size} 种药品即将到期"
        }
        val content = buildString {
            if (expired.isNotEmpty()) append(expired.joinToString("、") { it.name })
            if (expired.isNotEmpty() && expiring.isNotEmpty()) append("；")
            if (expiring.isNotEmpty()) {
                append(expiring.joinToString("、") { "${it.name}（${it.expiryShortLabel}）" })
            }
        }

        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    fun insightSummary(engine: InsightEngine, context: Context): String =
        engine.build(runCatching { MediBoxDbHelper(context).loadMedicines() }.getOrDefault(emptyList()))
            .firstOrNull()?.title.orEmpty()

    // ---------------- 服药提醒 ----------------

    private fun ensureDoseChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_DOSE) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_DOSE,
                        "服药提醒",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "按服药计划在设定时间提醒打卡"
                    }
                )
            }
        }
    }

    fun notifyDose(
        context: Context,
        planId: String,
        time: String,
        medicineName: String,
        dose: String,
        memberName: String = "",
        missed: Boolean = false,
        elder: Boolean = false
    ) {
        ensureDoseChannel(context)
        val id = doseNotifyId(planId, time)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // 老人的提醒直接打开大字打卡页，普通提醒还是打开用药提醒页。
            if (elder) putExtra(MainActivity.EXTRA_OPEN_ELDER_DOSES, true)
            else {
                putExtra(MainActivity.EXTRA_OPEN_REMINDERS, true)
                // 带上这一条服药，打开后直接高亮对应的时间点
                putExtra(MainActivity.EXTRA_FOCUS_DOSE, "$planId@$time")
            }
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val amount = if (dose.isBlank()) "按照计划用量" else dose
        val who = if (memberName.isBlank()) "" else "$memberName："
        val content = when {
            elder && missed ->
                "「$medicineName」$amount 还没打卡，吃过请按下面的「已吃了」，家人就放心了。"
            elder -> "${who}该吃「$medicineName」了，$amount。吃完请按下面的「已吃了」。"
            missed -> "$who「$medicineName」$amount 过点还没打卡，已经吃过就点「已服」。"
            else -> "$who「$medicineName」$amount，吃过后回来点一下打卡。"
        }
        val title = when {
            elder && memberName.isNotBlank() && missed -> "$memberName 还没打卡 · $time"
            elder && memberName.isNotBlank() -> "$memberName 该吃药了 · $time"
            missed -> "还没打卡 · $time"
            else -> "该吃药了 · $time"
        }

        val takenPending = actionPending(
            context, id + 1, DoseActionReceiver.ACTION_TAKEN,
            planId, time, medicineName, dose, memberName
        )
        val snoozePending = actionPending(
            context, id + 2, DoseActionReceiver.ACTION_SNOOZE,
            planId, time, medicineName, dose, memberName
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DOSE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .addAction(
                R.drawable.ic_notification,
                if (elder) "已吃了" else "已服",
                takenPending
            )
            .addAction(R.drawable.ic_notification, "稍后提醒", snoozePending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    fun cancelDose(context: Context, planId: String, time: String) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(doseNotifyId(planId, time))
        }
    }

    // ---------------- 家庭组云端提醒 ----------------

    private fun ensureFamilyChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_FAMILY) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_FAMILY,
                        "家庭提醒",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "家人未按时打卡、家属升级提醒等家庭组消息"
                    }
                )
            }
        }
    }

    /** 展示后端排队的提醒：到点、漏服，或家属收到的升级提醒。 */
    fun notifyRemote(
        context: Context,
        id: Long,
        title: String,
        body: String,
        escalation: Boolean
    ) {
        ensureFamilyChannel(context)
        val notifyId = REMOTE_BASE_ID + (id % 10_000L).toInt()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_REMINDERS, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            notifyId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_FAMILY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "家庭提醒" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (escalation) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(notifyId, notification)
        }
    }

    private fun doseNotifyId(planId: String, time: String): Int =
        NOTIFICATION_ID + "$planId@$time".hashCode()

    private fun actionPending(
        context: Context,
        requestCode: Int,
        action: String,
        planId: String,
        time: String,
        medicineName: String,
        dose: String,
        memberName: String
    ): PendingIntent {
        val intent = Intent(context, DoseActionReceiver::class.java).apply {
            this.action = action
            putExtra(DoseAlarmReceiver.EXTRA_PLAN_ID, planId)
            putExtra(DoseAlarmReceiver.EXTRA_TIME, time)
            putExtra(DoseAlarmReceiver.EXTRA_NAME, medicineName)
            putExtra(DoseAlarmReceiver.EXTRA_DOSE, dose)
            putExtra(DoseAlarmReceiver.EXTRA_MEMBER, memberName)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
