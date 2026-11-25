package com.family.medbox.data.backup

import com.family.medbox.data.db.MediBoxDbHelper
import com.family.medbox.data.model.MedicationPlan
import com.family.medbox.data.model.Medicine
import com.family.medbox.data.model.Member
import com.family.medbox.data.prefs.MedicationPlanStore
import com.family.medbox.data.prefs.ShoppingItem
import com.family.medbox.data.prefs.ShoppingStore
import com.family.medbox.util.DateUtils
import org.json.JSONArray
import org.json.JSONObject

/**
 * 药箱数据备份：把药品、成员、服药计划、打卡记录和采购清单导出成一份 JSON，
 * 换手机时再导回来。文件由用户自己选择保存位置，App 不额外上传。
 */
class BackupManager(
    private val helper: MediBoxDbHelper,
    private val planStore: MedicationPlanStore,
    private val shoppingStore: ShoppingStore
) {

    fun exportJson(): String {
        val medicines = helper.loadMedicines()
        val members = helper.loadMembers()
        val root = JSONObject().apply {
            put("app", APP_TAG)
            put("version", VERSION)
            put("exportedAt", DateUtils.timestamp())
            put("medicines", JSONArray().apply { medicines.forEach { put(it.toJson()) } })
            put("members", JSONArray().apply { members.forEach { put(it.toJson()) } })
            put("plans", JSONArray().apply { planStore.plans.value.forEach { put(it.toJson()) } })
            put(
                "checks",
                JSONObject().apply {
                    planStore.checks.value.forEach { (date, keys) -> put(date, JSONArray(keys.toList())) }
                }
            )
            put("remindersEnabled", planStore.remindersEnabled.value)
            put("shopping", JSONArray().apply { shoppingStore.items.value.forEach { put(it.toJson()) } })
        }
        return root.toString(2)
    }

    /** 备份文件摘要，用于导入前的确认弹窗。 */
    data class Preview(val medicines: Int, val members: Int, val plans: Int, val exportedAt: String)

    fun preview(raw: String): Preview {
        val root = parse(raw)
        return Preview(
            medicines = root.optJSONArray("medicines")?.length() ?: 0,
            members = root.optJSONArray("members")?.length() ?: 0,
            plans = root.optJSONArray("plans")?.length() ?: 0,
            exportedAt = root.optString("exportedAt", "")
        )
    }

    /** 覆盖式恢复：先清空现有数据，再写入备份内容。返回恢复的药品数。 */
    fun restore(raw: String): Int {
        val root = parse(raw)
        val members = root.optJSONArray("members").toMembers()
        val medicines = root.optJSONArray("medicines").toMedicines()
        val plans = root.optJSONArray("plans").toPlans()
        val checks = root.optJSONObject("checks").toChecks()
        val shopping = root.optJSONArray("shopping").toShopping()

        helper.replaceAll(medicines, members)
        planStore.restore(plans, checks)
        planStore.setRemindersEnabled(root.optBoolean("remindersEnabled", true))
        shoppingStore.replaceAll(shopping)
        return medicines.size
    }

    private fun parse(raw: String): JSONObject {
        val root = runCatching { JSONObject(raw) }
            .getOrElse { throw IllegalArgumentException("文件不是有效的备份") }
        if (root.optString("app") != APP_TAG) throw IllegalArgumentException("这不是药箱的备份文件")
        if (root.optInt("version", 0) > VERSION) throw IllegalArgumentException("备份来自更新版本，请先升级 App")
        return root
    }

    // ---------------- 序列化 ----------------

    private fun Medicine.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("ingredient", ingredient)
        put("category", category)
        put("spec", spec)
        put("form", form)
        put("quantity", quantity)
        put("unit", unit)
        put("expiryDate", expiryDate ?: "")
        put("memberId", memberId ?: 0L)
        put("location", location)
        put("note", note)
        put("createdAt", createdAt)
    }

    private fun Member.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("relation", relation)
        put("birthday", birthday ?: "")
        put("colorIndex", colorIndex)
        put("isSelf", isSelf)
        put("needsCare", needsCare)
    }

    private fun MedicationPlan.toJson() = JSONObject().apply {
        put("id", id)
        put("name", medicineName)
        put("dose", dose)
        put("memberId", memberId ?: 0L)
        put("memberName", memberName)
        put("times", JSONArray(times))
        put("startDate", startDate)
        put("days", days)
    }

    private fun ShoppingItem.toJson() = JSONObject().apply {
        put("name", name)
        put("reason", reason)
        put("addedAt", addedAt)
    }

    // ---------------- 反序列化 ----------------

    private fun JSONArray?.toMembers(): List<Member> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = optJSONObject(i) ?: continue
                val name = item.optString("name").trim()
                if (name.isEmpty()) continue
                add(
                    Member(
                        id = item.optLong("id"),
                        name = name,
                        relation = item.optString("relation", "家庭成员"),
                        birthday = item.optString("birthday").ifBlank { null },
                        colorIndex = item.optInt("colorIndex"),
                        isSelf = item.optBoolean("isSelf"),
                        needsCare = item.optBoolean("needsCare")
                    )
                )
            }
        }
    }

    private fun JSONArray?.toMedicines(): List<Medicine> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = optJSONObject(i) ?: continue
                val name = item.optString("name").trim()
                if (name.isEmpty()) continue
                add(
                    Medicine(
                        id = item.optLong("id"),
                        name = name,
                        ingredient = item.optString("ingredient"),
                        category = item.optString("category"),
                        spec = item.optString("spec"),
                        form = item.optString("form"),
                        quantity = item.optInt("quantity"),
                        unit = item.optString("unit", "份"),
                        expiryDate = item.optString("expiryDate").ifBlank { null },
                        memberId = item.optLong("memberId").takeIf { it > 0L },
                        location = item.optString("location"),
                        note = item.optString("note"),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    private fun JSONArray?.toPlans(): List<MedicationPlan> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = optJSONObject(i) ?: continue
                val id = item.optString("id")
                val name = item.optString("name")
                if (id.isEmpty() || name.isEmpty()) continue
                val times = item.optJSONArray("times")?.let { array ->
                    buildList { for (t in 0 until array.length()) add(array.getString(t)) }
                } ?: listOf("08:00")
                add(
                    MedicationPlan(
                        id = id,
                        medicineName = name,
                        dose = item.optString("dose", "1 次用量"),
                        memberId = item.optLong("memberId").takeIf { it > 0L },
                        memberName = item.optString("memberName"),
                        times = times,
                        startDate = item.optString("startDate"),
                        days = item.optInt("days", 7)
                    )
                )
            }
        }
    }

    private fun JSONObject?.toChecks(): Map<String, Set<String>> {
        if (this == null) return emptyMap()
        return buildMap {
            keys().forEach { date ->
                val array = optJSONArray(date) ?: return@forEach
                put(
                    date,
                    buildSet { for (i in 0 until array.length()) add(array.getString(i)) }
                )
            }
        }
    }

    private fun JSONArray?.toShopping(): List<ShoppingItem> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = optJSONObject(i) ?: continue
                val name = item.optString("name").trim()
                if (name.isEmpty()) continue
                add(
                    ShoppingItem(
                        name = name,
                        reason = item.optString("reason"),
                        addedAt = item.optLong("addedAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    companion object {
        private const val APP_TAG = "medibox"
        private const val VERSION = 1

        fun suggestedFileName(): String = "药箱备份-${DateUtils.toIso(DateUtils.today())}.json"
    }
}
