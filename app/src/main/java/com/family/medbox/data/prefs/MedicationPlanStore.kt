package com.family.medbox.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.family.medbox.data.model.MedicationPlan
import com.family.medbox.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地计划里尚未同步到服务端的字段变更。
 * [at] 是这次编辑的时间，服务端按字段比较，时间新的值胜出（LWW）。
 */
data class PlanChange(val fields: Set<String>, val at: String)

/**
 * 服药计划与打卡记录：轻量 JSON 存在 SharedPreferences，
 * 避免为演示项目升级数据库版本时重建数据。
 */
class MedicationPlanStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _plans = MutableStateFlow(readPlans())
    val plans: StateFlow<List<MedicationPlan>> = _plans.asStateFlow()

    private val _changes = MutableStateFlow(readChanges())

    /** 计划 id -> 还没同步出去的字段，供同步器只推送真正改过的部分。 */
    val changes: StateFlow<Map<String, PlanChange>> = _changes.asStateFlow()

    private val _checks = MutableStateFlow(readChecks())
    val checks: StateFlow<Map<String, Set<String>>> = _checks.asStateFlow()

    private val _remindersEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_REMINDERS_ENABLED, true))
    val remindersEnabled: StateFlow<Boolean> = _remindersEnabled.asStateFlow()

    // 必须持有强引用：SharedPreferences 内部用弱引用保存监听器，lambda 会被回收。
    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                KEY_PLANS -> _plans.value = readPlans()
                KEY_CHECKS -> _checks.value = readChecks()
                KEY_REMINDERS_ENABLED ->
                    _remindersEnabled.value = prefs.getBoolean(KEY_REMINDERS_ENABLED, true)
            }
        }

    init {
        // 通知按钮等场景会新建 Store 写入 prefs，这里同步回内存态，避免界面滞后。
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        seedDemoIfFirstRun()
    }

    /**
     * 首次启动写入演示计划与最近两周的打卡记录，
     * 让首页、本周打卡、老人打卡页一上手就有真实内容。
     */
    private fun seedDemoIfFirstRun() {
        if (prefs.contains(KEY_PLANS)) return

        val today = DateUtils.today()
        val startDate = DateUtils.toIso(today.minusDays(10))
        val demoPlans = listOf(
            MedicationPlan(
                id = "demo-plan-1",
                medicineName = "络活喜 苯磺酸氨氯地平片",
                dose = "1 片",
                memberId = 4,
                memberName = "爷爷",
                times = listOf("08:00"),
                startDate = startDate,
                days = 30
            ),
            MedicationPlan(
                id = "demo-plan-2",
                medicineName = "格华止 盐酸二甲双胍缓释片",
                dose = "1 片",
                memberId = 5,
                memberName = "奶奶",
                times = listOf("08:00", "18:00"),
                startDate = startDate,
                days = 30
            ),
            MedicationPlan(
                id = "demo-plan-3",
                medicineName = "星鲨 维生素D滴剂",
                dose = "1 粒",
                memberId = 1,
                memberName = "我",
                times = listOf("09:00"),
                startDate = startDate,
                days = 30
            ),
            MedicationPlan(
                id = "demo-plan-4",
                medicineName = "开瑞坦 氯雷他定片",
                dose = "1 片",
                memberId = 2,
                memberName = "妈妈",
                times = listOf("21:00"),
                startDate = startDate,
                days = 30
            )
        )
        _plans.value = demoPlans
        writePlans()

        // 过去两周按固定规律跳过少量打卡，避免看起来像「全是满分」的假数据。
        val checks = LinkedHashMap<String, Set<String>>()
        for (offset in 0L..13L) {
            val date = DateUtils.toIso(today.minusDays(offset))
            val keys = mutableSetOf<String>()
            demoPlans.forEach { plan ->
                plan.times.forEach { time ->
                    val skip = when {
                        offset == 0L -> time == "18:00" || time == "21:00"
                        time == "08:00" -> offset % 6L == 0L
                        time == "18:00" -> offset % 4L == 1L
                        time == "09:00" -> offset % 5L == 3L
                        time == "21:00" -> offset % 7L == 2L || offset % 7L == 4L
                        else -> false
                    }
                    if (!skip) keys += plan.checkKey(time)
                }
            }
            if (keys.isNotEmpty()) checks[date] = keys
        }
        _checks.value = checks
        writeChecks()
    }

    fun setRemindersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDERS_ENABLED, enabled).apply()
        _remindersEnabled.value = enabled
    }

    fun addPlan(plan: MedicationPlan) {
        val previous = _plans.value.firstOrNull { it.id == plan.id }
        _plans.value = _plans.value.filterNot { it.id == plan.id } + plan
        writePlans()
        markChanged(plan.id, changedFields(previous, plan))
    }

    fun deletePlan(planId: String) {
        val existed = _plans.value.any { it.id == planId }
        _plans.value = _plans.value.filterNot { it.id == planId }
        _checks.value = _checks.value.mapValues { (_, keys) ->
            keys.filterNot { it.startsWith("$planId@") }.toSet()
        }.filterValues { it.isNotEmpty() }
        writePlans()
        writeChecks()
        // 删除也是一次字段变更，服务端用它和别的设备的编辑比时间（墓碑）。
        if (existed) markChanged(planId, setOf(FIELD_DELETED))
    }

    fun changeOf(planId: String): PlanChange? = _changes.value[planId]

    /** 推送成功后清掉脏标记；下次同步就不会重复上传没改过的字段。 */
    fun clearChange(planId: String) {
        if (planId !in _changes.value) return
        _changes.value = _changes.value - planId
        writeChanges()
    }

    private fun markChanged(planId: String, fields: Set<String>) {
        if (fields.isEmpty()) return
        val merged = _changes.value[planId]?.fields.orEmpty() + fields
        _changes.value = _changes.value + (planId to PlanChange(merged, DateUtils.timestamp()))
        writeChanges()
    }

    private fun changedFields(before: MedicationPlan?, after: MedicationPlan): Set<String> {
        // 从没同步过的新计划：所有字段都要上传。
        if (before == null) return SYNCED_FIELDS.toSet()
        return buildSet {
            if (before.medicineName != after.medicineName) add("medicine_name")
            if (before.dose != after.dose) add("dose")
            if (before.times != after.times) add("times")
            if (before.startDate != after.startDate) add("start_date")
            if (before.days != after.days) add("days")
        }
    }

    /** 从备份整体恢复计划与打卡记录。 */
    fun restore(plans: List<MedicationPlan>, checks: Map<String, Set<String>>) {
        _plans.value = plans
        _checks.value = checks
        writePlans()
        writeChecks()
    }

    fun toggleCheck(date: String, planId: String, time: String) {
        val checked = "$planId@$time" !in _checks.value[date].orEmpty()
        setChecked(date, planId, time, checked)
    }

    /** 直接设置某次服药是否已打卡（通知上的「已服」按钮用）。 */
    fun setChecked(date: String, planId: String, time: String, checked: Boolean) {
        val key = "$planId@$time"
        val day = _checks.value[date].orEmpty()
        val updated = if (checked) day + key else day - key
        _checks.value = if (updated.isEmpty()) {
            _checks.value - date
        } else {
            _checks.value + (date to updated)
        }
        writeChecks()
    }

    fun isChecked(date: String, planId: String, time: String): Boolean =
        "$planId@$time" in _checks.value[date].orEmpty()

    /** 应用云端拉取结果：按 id 覆盖或新增，墓碑计划直接移除。 */
    fun applyRemote(upserts: List<MedicationPlan>, removeIds: Set<String>) {
        val merged = LinkedHashMap<String, MedicationPlan>()
        _plans.value.forEach { merged[it.id] = it }
        removeIds.forEach { merged.remove(it) }
        upserts.forEach { merged[it.id] = it }
        _plans.value = merged.values.toList()
        writePlans()
    }

    private fun readPlans(): List<MedicationPlan> {
        val raw = prefs.getString(KEY_PLANS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        MedicationPlan(
                            id = item.getString("id"),
                            medicineName = item.getString("name"),
                            dose = item.optString("dose", "1 次用量"),
                            memberId = if (item.has("memberId")) item.getLong("memberId") else null,
                            memberName = item.optString("memberName", ""),
                            times = item.optJSONArray("times")?.let { times ->
                                buildList {
                                    for (t in 0 until times.length()) add(times.getString(t))
                                }
                            } ?: listOf("08:00"),
                            startDate = item.optString("startDate", ""),
                            days = item.optInt("days", 7)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writePlans() {
        val array = JSONArray()
        _plans.value.forEach { plan ->
            array.put(
                JSONObject().apply {
                    put("id", plan.id)
                    put("name", plan.medicineName)
                    put("dose", plan.dose)
                    plan.memberId?.let { put("memberId", it) }
                    put("memberName", plan.memberName)
                    put("times", JSONArray(plan.times))
                    put("startDate", plan.startDate)
                    put("days", plan.days)
                }
            )
        }
        prefs.edit().putString(KEY_PLANS, array.toString()).apply()
    }

    private fun readChanges(): Map<String, PlanChange> {
        val raw = prefs.getString(KEY_CHANGES, null) ?: return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                root.keys().forEach { id ->
                    val item = root.optJSONObject(id) ?: return@forEach
                    val fields = item.optJSONArray("fields")?.let { array ->
                        buildSet { for (i in 0 until array.length()) add(array.getString(i)) }
                    }.orEmpty()
                    put(id, PlanChange(fields, item.optString("at")))
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun writeChanges() {
        val root = JSONObject()
        _changes.value.forEach { (id, change) ->
            root.put(
                id,
                JSONObject().apply {
                    put("fields", JSONArray(change.fields.toList()))
                    put("at", change.at)
                }
            )
        }
        prefs.edit().putString(KEY_CHANGES, root.toString()).apply()
    }

    private fun readChecks(): Map<String, Set<String>> {
        val raw = prefs.getString(KEY_CHECKS, null) ?: return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                root.keys().forEach { date ->
                    val array = root.optJSONArray(date) ?: return@forEach
                    put(
                        date,
                        buildSet {
                            for (i in 0 until array.length()) add(array.getString(i))
                        }
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun writeChecks() {
        val root = JSONObject()
        _checks.value.forEach { (date, keys) ->
            root.put(date, JSONArray(keys.toList()))
        }
        prefs.edit().putString(KEY_CHECKS, root.toString()).apply()
    }

    companion object {
        private const val PREF_NAME = "medibox_dose_plans"
        private const val KEY_PLANS = "plans"
        private const val KEY_CHECKS = "checks"
        private const val KEY_CHANGES = "plan_changes"
        private const val KEY_REMINDERS_ENABLED = "reminders_enabled"

        private const val FIELD_DELETED = "deleted"

        /** 会参与云端同步的计划字段，命名与服务端一致。 */
        val SYNCED_FIELDS = listOf("medicine_name", "dose", "times", "start_date", "days")
    }
}
