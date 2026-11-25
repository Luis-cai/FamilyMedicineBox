package com.family.medbox.data.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 家庭同步的本地配置：服务器地址、登录态、家庭组与上次同步时间。 */
data class SyncInfo(
    val serverUrl: String = DEFAULT_SERVER,
    val token: String = "",
    val userId: Long = 0L,
    val nickname: String = "",
    val phone: String = "",
    val familyId: Long? = null,
    val familyName: String = "",
    val inviteCode: String = "",
    val memberId: Long? = null,
    val enabled: Boolean = true,
    val lastSyncAt: String = ""
) {
    val loggedIn: Boolean get() = token.isNotBlank()
    val inFamily: Boolean get() = familyId != null && memberId != null

    companion object {
        /** 模拟器先执行 adb reverse tcp:8000 tcp:8000；真机改成电脑局域网 IP。 */
        const val DEFAULT_SERVER = "http://127.0.0.1:8000"
    }
}

class SyncStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _info = MutableStateFlow(readInfo())
    val info: StateFlow<SyncInfo> = _info.asStateFlow()

    fun update(transform: (SyncInfo) -> SyncInfo) {
        val next = transform(_info.value)
        _info.value = next
        writeInfo(next)
    }

    fun clearSession() {
        update { SyncInfo(serverUrl = it.serverUrl, nickname = it.nickname, phone = it.phone) }
        prefs.edit().remove(KEY_PULLED).apply()
    }

    /** 从云端拉下来的计划 id：避免下次同步又把它们当成「本机计划」推回去。 */
    fun pulledIds(): Set<String> = runCatching {
        val raw = prefs.getString(KEY_PULLED, null) ?: return emptySet()
        val array = JSONArray(raw)
        buildSet { for (i in 0 until array.length()) add(array.getString(i)) }
    }.getOrDefault(emptySet())

    fun addPulledIds(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val merged = pulledIds() + ids
        prefs.edit().putString(KEY_PULLED, JSONArray(merged.toList()).toString()).apply()
    }

    fun removePulledIds(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val remaining = pulledIds() - ids.toSet()
        prefs.edit().putString(KEY_PULLED, JSONArray(remaining.toList()).toString()).apply()
    }

    private fun readInfo(): SyncInfo {
        val raw = prefs.getString(KEY_INFO, null) ?: return SyncInfo()
        return runCatching {
            val item = JSONObject(raw)
            SyncInfo(
                serverUrl = item.optString("serverUrl", SyncInfo.DEFAULT_SERVER),
                token = item.optString("token", ""),
                userId = item.optLong("userId", 0L),
                nickname = item.optString("nickname", ""),
                phone = item.optString("phone", ""),
                familyId = if (item.has("familyId")) item.getLong("familyId") else null,
                familyName = item.optString("familyName", ""),
                inviteCode = item.optString("inviteCode", ""),
                memberId = if (item.has("memberId")) item.getLong("memberId") else null,
                enabled = item.optBoolean("enabled", true),
                lastSyncAt = item.optString("lastSyncAt", "")
            )
        }.getOrDefault(SyncInfo())
    }

    private fun writeInfo(info: SyncInfo) {
        val item = JSONObject().apply {
            put("serverUrl", info.serverUrl)
            put("token", info.token)
            put("userId", info.userId)
            put("nickname", info.nickname)
            put("phone", info.phone)
            info.familyId?.let { put("familyId", it) }
            put("familyName", info.familyName)
            put("inviteCode", info.inviteCode)
            info.memberId?.let { put("memberId", it) }
            put("enabled", info.enabled)
            put("lastSyncAt", info.lastSyncAt)
        }
        prefs.edit().putString(KEY_INFO, item.toString()).apply()
    }

    companion object {
        private const val PREF_NAME = "medibox_sync"
        private const val KEY_INFO = "session"
        private const val KEY_PULLED = "pulled_ids"
    }
}
