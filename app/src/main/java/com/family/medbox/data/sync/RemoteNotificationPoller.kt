package com.family.medbox.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.family.medbox.data.prefs.MedicationPlanStore
import com.family.medbox.notify.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray

sealed class PollResult {
    data class Ok(val shown: Int, val acked: Boolean) : PollResult()
    object Disabled : PollResult()
    object Skipped : PollResult()
    object Offline : PollResult()
    data class Failed(val reason: String?) : PollResult()
}

/**
 * 拉取家庭组排队中的提醒并在本机展示。
 *
 * 优化点：
 * - 单飞：闹钟与界面回调同时触发时只有一次真正请求；
 * - 限频：20 秒内不重复请求；
 * - 去重：即使回执失败，已展示过的提醒也不会重复弹；
 * - 离线不空跑：没有网络时直接返回，不浪费一次请求与退避计数；
 * - 回执补发：ack 失败的 id 会留在本地，下次成功后一并补发，避免服务端积压；
 * - 与本地闹钟互补：本机已有该计划且提醒开启时，「到点」交给本地闹钟。
 */
class RemoteNotificationPoller(
    private val context: Context,
    private val syncStore: SyncStore,
    private val planStore: MedicationPlanStore,
    private val client: SyncClient = SyncClient()
) {

    private val mutex = Mutex()
    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // 只在进程内限频：冷启动、闹钟唤醒都应立即拉一次，不能被上次进程的时间戳挡住。
    @Volatile
    private var lastAttemptAt = 0L

    suspend fun pull(force: Boolean = false): PollResult {
        if (!mutex.tryLock()) return PollResult.Skipped
        try {
            val session = syncStore.info.value
            if (!session.enabled || !session.loggedIn || !session.inFamily) {
                return PollResult.Disabled
            }

            // 没网时不发请求，也不占用限频窗口，网络恢复后可以立刻补拉。
            if (!isOnline()) return PollResult.Offline

            val now = System.currentTimeMillis()
            if (!force && now - lastAttemptAt < MIN_GAP_MILLIS) {
                return PollResult.Skipped
            }
            lastAttemptAt = now

            return withContext(Dispatchers.IO) {
                runCatching {
                    // 先补发上次没成功的回执，避免服务端 pending 越积越多。
                    val flushed = flushPendingAck(session.serverUrl, session.token)

                    val pending = client.pullNotifications(session.serverUrl, session.token)
                    if (pending.isEmpty()) {
                        return@runCatching PollResult.Ok(0, acked = flushed)
                    }

                    val shownKeys = readShownKeys()
                    var shown = 0
                    pending.forEach { item ->
                        val key = "${item.id}@${item.createdAt}"
                        if (key in shownKeys) return@forEach
                        val handledLocally = item.kind == "due" &&
                            planStore.remindersEnabled.value &&
                            planStore.plans.value.any { it.id == item.planId && item.time in it.times }
                        if (!handledLocally) {
                            NotificationHelper.notifyRemote(
                                context = context,
                                id = item.id,
                                title = item.title,
                                body = item.body,
                                escalation = item.kind != "due"
                            )
                            shown += 1
                        }
                        shownKeys += key
                    }
                    writeShownKeys(shownKeys)

                    val ids = pending.map { it.id }
                    val acked = runCatching {
                        client.ackNotifications(session.serverUrl, session.token, ids)
                    }.onSuccess { clearPendingAck() }
                        .onFailure { appendPendingAck(ids) }
                        .isSuccess
                    PollResult.Ok(shown, acked)
                }.getOrElse { PollResult.Failed(it.message) }
            }
        } finally {
            mutex.unlock()
        }
    }

    /** 退出登录时清掉本机去重与限频状态。 */
    fun reset() {
        prefs.edit().clear().apply()
    }

    private fun readShownKeys(): MutableSet<String> {
        val raw = prefs.getString(KEY_SHOWN, null) ?: return LinkedHashSet()
        return runCatching {
            val array = JSONArray(raw)
            val keys = LinkedHashSet<String>(array.length())
            for (i in 0 until array.length()) keys += array.getString(i)
            keys
        }.getOrDefault(LinkedHashSet())
    }

    private fun writeShownKeys(keys: Set<String>) {
        val kept = keys.toList().takeLast(MAX_SHOWN_IDS)
        prefs.edit().putString(KEY_SHOWN, JSONArray(kept).toString()).apply()
    }

    private fun flushPendingAck(serverUrl: String, token: String): Boolean {
        val ids = readPendingAck()
        if (ids.isEmpty()) return true
        return runCatching { client.ackNotifications(serverUrl, token, ids) }
            .onSuccess { clearPendingAck() }
            .isSuccess
    }

    private fun appendPendingAck(ids: List<Long>) {
        val merged = (readPendingAck() + ids).distinct().takeLast(MAX_PENDING_ACK)
        prefs.edit().putString(KEY_PENDING_ACK, JSONArray(merged).toString()).apply()
    }

    private fun readPendingAck(): List<Long> {
        val raw = prefs.getString(KEY_PENDING_ACK, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList { for (i in 0 until array.length()) add(array.getLong(i)) }
        }.getOrDefault(emptyList())
    }

    private fun clearPendingAck() {
        prefs.edit().remove(KEY_PENDING_ACK).apply()
    }

    private fun isOnline(): Boolean {
        val manager = context.applicationContext
            .getSystemService(ConnectivityManager::class.java) ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        const val PREF_NAME = "medibox_remote_poll"
        const val KEY_SHOWN = "shown_ids"
        const val KEY_PENDING_ACK = "pending_ack_ids"
        const val MIN_GAP_MILLIS = 20_000L
        const val MAX_SHOWN_IDS = 200
        const val MAX_PENDING_ACK = 100
    }
}
