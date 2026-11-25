package com.family.medbox.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 指标类型：不同类型用到的字段不一样，记录里统一留空。 */
enum class HealthKind(val label: String, val unit: String) {
    BP("血压", "mmHg"),
    GLUCOSE("血糖", "mmol/L"),
    TEMPERATURE("体温", "℃"),
    SYMPTOM("症状", "")
}

/** 一条身体指标记录，只存本机，可随时删除。 */
data class HealthRecord(
    val id: String,
    val memberId: Long,
    val memberName: String,
    val kind: HealthKind,
    val dateIso: String,
    val timeLabel: String,
    val systolic: Int = 0,
    val diastolic: Int = 0,
    val value: Double = 0.0,
    val note: String = ""
) {
    val displayValue: String
        get() = when (kind) {
            HealthKind.BP -> "$systolic/$diastolic"
            HealthKind.GLUCOSE, HealthKind.TEMPERATURE -> "%.1f".format(value)
            HealthKind.SYMPTOM -> note.take(12).ifBlank { "症状" }
        }

    /** 趋势图用的数值：血压取收缩压。 */
    val chartValue: Double?
        get() = when (kind) {
            HealthKind.BP -> systolic.toDouble()
            HealthKind.GLUCOSE, HealthKind.TEMPERATURE -> value
            HealthKind.SYMPTOM -> null
        }

    /** 仅按常见参考范围给提示，不做诊断。 */
    val abnormal: Boolean
        get() = when (kind) {
            HealthKind.BP -> systolic >= 140 || diastolic >= 90 || systolic < 90 || diastolic < 60
            HealthKind.GLUCOSE -> value >= 7.0 || value < 3.9
            HealthKind.TEMPERATURE -> value >= 37.3
            HealthKind.SYMPTOM -> false
        }

    val abnormalLabel: String
        get() = when (kind) {
            HealthKind.BP -> when {
                systolic >= 140 || diastolic >= 90 -> "偏高"
                systolic < 90 || diastolic < 60 -> "偏低"
                else -> ""
            }
            HealthKind.GLUCOSE -> when {
                value >= 7.0 -> "偏高"
                value < 3.9 -> "偏低"
                else -> ""
            }
            HealthKind.TEMPERATURE -> if (value >= 37.3) "发热" else ""
            HealthKind.SYMPTOM -> ""
        }
}

class HealthLogStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _records = MutableStateFlow(loadAll())
    val records: StateFlow<List<HealthRecord>> = _records.asStateFlow()

    fun add(record: HealthRecord) {
        val next = (_records.value + record).sortedWith(compareByDescending<HealthRecord> { it.dateIso + it.timeLabel })
        prefs.edit().putString(KEY_RECORDS, encode(next)).apply()
        _records.value = next
    }

    fun delete(id: String) {
        val next = _records.value.filterNot { it.id == id }
        prefs.edit().putString(KEY_RECORDS, encode(next)).apply()
        _records.value = next
    }

    private fun loadAll(): List<HealthRecord> {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    decode(array.optJSONObject(i) ?: continue)?.let { add(it) }
                }
            }.sortedWith(compareByDescending { it.dateIso + it.timeLabel })
        }.getOrDefault(emptyList())
    }

    private fun encode(records: List<HealthRecord>): String {
        val array = JSONArray()
        records.forEach { record ->
            array.put(JSONObject().apply {
                put("id", record.id)
                put("memberId", record.memberId)
                put("memberName", record.memberName)
                put("kind", record.kind.name)
                put("dateIso", record.dateIso)
                put("timeLabel", record.timeLabel)
                put("systolic", record.systolic)
                put("diastolic", record.diastolic)
                put("value", record.value)
                put("note", record.note)
            })
        }
        return array.toString()
    }

    private fun decode(json: JSONObject): HealthRecord? {
        val kind = runCatching { HealthKind.valueOf(json.optString("kind")) }.getOrNull()
            ?: return null
        val id = json.optString("id").ifBlank { return null }
        return HealthRecord(
            id = id,
            memberId = json.optLong("memberId"),
            memberName = json.optString("memberName"),
            kind = kind,
            dateIso = json.optString("dateIso"),
            timeLabel = json.optString("timeLabel"),
            systolic = json.optInt("systolic"),
            diastolic = json.optInt("diastolic"),
            value = json.optDouble("value"),
            note = json.optString("note")
        )
    }

    private companion object {
        const val PREF_NAME = "medibox_health"
        const val KEY_RECORDS = "records"
    }
}
