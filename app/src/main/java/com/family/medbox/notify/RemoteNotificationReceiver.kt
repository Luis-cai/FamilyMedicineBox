package com.family.medbox.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.family.medbox.MediBoxApp
import com.family.medbox.data.sync.PollResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

/** 定时拉取家庭组提醒：后端负责生成，App 负责展示。 */
class RemoteNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? MediBoxApp ?: return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val poll = app.container.remotePoller.pull()
                RemoteNotificationScheduler.afterPoll(context, poll)
            } finally {
                result.finish()
            }
        }
    }
}

/**
 * 用「一次性闹钟 + 轮询后重排」代替固定重复闹钟：
 * - 15 分钟基准间隔，带 ±15 秒抖动，避免同家庭设备同时打满服务端；
 * - 失败按 2 / 5 / 15 分钟退避，恢复后回到基准间隔；
 * - 同步关闭或退出登录时直接取消，不再唤醒设备；
 * - 用 setAndAllowWhileIdle，Doze 下也能按系统允许的频率补拉。
 */
object RemoteNotificationScheduler {

    private const val REQUEST_CODE = 3001
    private const val INTERVAL_MILLIS = 15L * 60 * 1000
    private const val OFFLINE_RETRY_MILLIS = 60L * 1000
    private const val JITTER_MILLIS = 15_000L

    private const val PREF_NAME = "medibox_remote_poll"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_FAILS = "fails"

    fun start(context: Context) {
        prefs(context).edit().putBoolean(KEY_ENABLED, true).apply()
        // 启动/登录时界面会立即拉一次，闹钟直接进入 15 分钟节奏，避免刚打开就重复请求。
        scheduleNext(context, INTERVAL_MILLIS)
    }

    fun stop(context: Context) {
        prefs(context).edit().putBoolean(KEY_ENABLED, false).remove(KEY_FAILS).apply()
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, RemoteNotificationReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            runCatching { manager.cancel(pending) }
        }
    }

    fun afterPoll(context: Context, result: PollResult) {
        when (result) {
            is PollResult.Disabled -> stop(context)
            is PollResult.Skipped -> scheduleNext(context, INTERVAL_MILLIS)
            // 离线不发请求，1 分钟后重试；不计入失败退避，联网后能马上接上。
            is PollResult.Offline -> scheduleNext(context, OFFLINE_RETRY_MILLIS)
            is PollResult.Ok -> {
                if (result.acked) {
                    prefs(context).edit().putInt(KEY_FAILS, 0).apply()
                    scheduleNext(context, INTERVAL_MILLIS)
                } else {
                    scheduleNext(context, nextBackoff(context))
                }
            }
            is PollResult.Failed -> scheduleNext(context, nextBackoff(context))
        }
    }

    private fun nextBackoff(context: Context): Long {
        val fails = prefs(context).getInt(KEY_FAILS, 0) + 1
        prefs(context).edit().putInt(KEY_FAILS, fails).apply()
        return when (fails) {
            1 -> 2 * 60 * 1000L
            2 -> 5 * 60 * 1000L
            else -> INTERVAL_MILLIS
        }
    }

    private fun scheduleNext(context: Context, delayMillis: Long) {
        if (!prefs(context).getBoolean(KEY_ENABLED, false)) return
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val jitter = Random.nextLong(-JITTER_MILLIS, JITTER_MILLIS + 1)
        val triggerAt = System.currentTimeMillis() + delayMillis.coerceAtLeast(0L) + jitter
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, RemoteNotificationReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
