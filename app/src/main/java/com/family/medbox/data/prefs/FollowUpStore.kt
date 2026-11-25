package com.family.medbox.data.prefs

import android.content.Context
import com.family.medbox.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 一次复诊安排：复诊日 + 处方可续到哪天，只存本机。 */
data class FollowUp(
    val id: String,
    val memberId: Long,
    val memberName: String,
    val hospital: String = "",
    val department: String = "",
    val visitIso: String = "",
    val prescriptionIso: String = "",
    val note: String = ""
) {
    val daysToVisit: Long? get() = DateUtils.daysLeft(visitIso)
    val daysToPrescription: Long? get() = DateUtils.daysLeft(prescriptionIso)

    val visitLabel: String
        get() {
            val days = daysToVisit ?: return "未填日期"
            return when {
                days < 0L -> "已过 ${-days} 天"
                days == 0L -> "今天复诊"
                days == 1L -> "明天复诊"
                else -> "还有 $days 天"
            }
        }

    /** 是否进入需要提前准备的时间窗。 */
    val needsPrep: Boolean
        get() = (daysToVisit ?: Long.MAX_VALUE) <= 3L ||
            (daysToPrescription ?: Long.MAX_VALUE) <= 7L
}

class FollowUpStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _items = MutableStateFlow(loadAll())
    val items: StateFlow<List<FollowUp>> = _items.asStateFlow()

    fun save(item: FollowUp) {
        val next = (_items.value.filterNot { it.id == item.id } + item)
            .sortedBy { it.visitIso.ifBlank { "9999-12-31" } }
        prefs.edit().putString(KEY_ITEMS, encode(next)).apply()
        _items.value = next
    }

    fun delete(id: String) {
        val next = _items.value.filterNot { it.id == id }
        prefs.edit().putString(KEY_ITEMS, encode(next)).apply()
        _items.value = next
    }

    private fun loadAll(): List<FollowUp> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    decode(array.optJSONObject(i) ?: continue)?.let { add(it) }
                }
            }.sortedBy { it.visitIso.ifBlank { "9999-12-31" } }
        }.getOrDefault(emptyList())
    }

    private fun encode(items: List<FollowUp>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("memberId", item.memberId)
                put("memberName", item.memberName)
                put("hospital", item.hospital)
                put("department", item.department)
                put("visitIso", item.visitIso)
                put("prescriptionIso", item.prescriptionIso)
                put("note", item.note)
            })
        }
        return array.toString()
    }

    private fun decode(json: JSONObject): FollowUp? {
        val id = json.optString("id").ifBlank { return null }
        return FollowUp(
            id = id,
            memberId = json.optLong("memberId"),
            memberName = json.optString("memberName"),
            hospital = json.optString("hospital"),
            department = json.optString("department"),
            visitIso = json.optString("visitIso"),
            prescriptionIso = json.optString("prescriptionIso"),
            note = json.optString("note")
        )
    }

    private companion object {
        const val PREF_NAME = "medibox_followup"
        const val KEY_ITEMS = "items"
    }
}
