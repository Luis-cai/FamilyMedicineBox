package com.family.medbox.data.sync

/**
 * 家庭动态：本机 SceneEvent 上报到家庭组后端后，家人设备拉取到的事件。
 */
data class FamilyEvent(
    val id: Long,
    val type: String,
    val actorName: String,
    val memberName: String,
    val medicineName: String,
    val time: String,
    val createdAt: String
) {

    val isDoseTaken: Boolean get() = type == TYPE_DOSE_TAKEN

    /** 「奶奶 18:00 已服药」 */
    fun title(): String {
        val who = memberName.ifBlank { actorName }
        return when (type) {
            TYPE_DOSE_TAKEN -> "$who $time 已服药"
            TYPE_DOSE_UNDO -> "$who $time 取消打卡"
            else -> "$who $time 有新的记录"
        }
    }

    /** 「11-20 18:02」，服务端固定返回 yyyy-MM-ddTHH:mm:ss */
    fun timeLabel(): String =
        if (createdAt.length >= 16) createdAt.substring(5, 16).replace("T", " ") else createdAt

    companion object {
        const val TYPE_DOSE_TAKEN = "dose_taken"
        const val TYPE_DOSE_UNDO = "dose_undo"

        fun fromJson(json: org.json.JSONObject): FamilyEvent = FamilyEvent(
            id = json.getLong("id"),
            type = json.optString("type"),
            actorName = json.optString("actor_name"),
            memberName = json.optString("member_name"),
            medicineName = json.optString("medicine_name"),
            time = json.optString("time"),
            createdAt = json.optString("created_at")
        )
    }
}
