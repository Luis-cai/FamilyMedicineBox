package com.family.medbox.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** 一位成员的急救信息，只存本机，不上传家庭组。 */
data class EmergencyInfo(
    val allergies: String = "",
    val conditions: String = "",
    val bloodType: String = "",
    val contactName: String = "",
    val contactPhone: String = "",
    val note: String = ""
) {
    val isBlank: Boolean
        get() = listOf(allergies, conditions, bloodType, contactName, contactPhone, note)
            .all { it.isBlank() }
}

class EmergencyStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _infos = MutableStateFlow(loadAll())
    val infos: StateFlow<Map<Long, EmergencyInfo>> = _infos.asStateFlow()

    fun save(memberId: Long, info: EmergencyInfo) {
        val next = _infos.value.toMutableMap()
        if (info.isBlank) {
            next.remove(memberId)
            prefs.edit().remove(KEY_PREFIX + memberId).apply()
        } else {
            next[memberId] = info
            prefs.edit().putString(KEY_PREFIX + memberId, encode(info)).apply()
        }
        _infos.value = next
    }

    private fun loadAll(): Map<Long, EmergencyInfo> = buildMap {
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(KEY_PREFIX) && value is String) {
                val id = key.removePrefix(KEY_PREFIX).toLongOrNull() ?: return@forEach
                runCatching { decode(value) }.getOrNull()?.let { put(id, it) }
            }
        }
    }

    private fun encode(info: EmergencyInfo): String = JSONObject().apply {
        put("allergies", info.allergies)
        put("conditions", info.conditions)
        put("bloodType", info.bloodType)
        put("contactName", info.contactName)
        put("contactPhone", info.contactPhone)
        put("note", info.note)
    }.toString()

    private fun decode(raw: String): EmergencyInfo {
        val json = JSONObject(raw)
        return EmergencyInfo(
            allergies = json.optString("allergies"),
            conditions = json.optString("conditions"),
            bloodType = json.optString("bloodType"),
            contactName = json.optString("contactName"),
            contactPhone = json.optString("contactPhone"),
            note = json.optString("note")
        )
    }

    private companion object {
        const val PREF_NAME = "medibox_emergency"
        const val KEY_PREFIX = "member_"
    }
}
