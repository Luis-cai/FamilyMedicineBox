package com.family.medbox.data.prefs

import android.content.Context

/**
 * 智能体的长期记忆：只保存上一次聊到的药品/成分/成员和最后一个问题，
 * 用于跨会话承接追问，不上传、不联网。
 */
class AgentMemoryStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    data class Snapshot(
        val medicineName: String = "",
        val ingredient: String = "",
        val memberName: String = "",
        val lastQuestion: String = ""
    )

    fun load(): Snapshot = Snapshot(
        medicineName = prefs.getString(KEY_MEDICINE, "").orEmpty(),
        ingredient = prefs.getString(KEY_INGREDIENT, "").orEmpty(),
        memberName = prefs.getString(KEY_MEMBER, "").orEmpty(),
        lastQuestion = prefs.getString(KEY_QUESTION, "").orEmpty()
    )

    fun save(snapshot: Snapshot) {
        prefs.edit()
            .putString(KEY_MEDICINE, snapshot.medicineName)
            .putString(KEY_INGREDIENT, snapshot.ingredient)
            .putString(KEY_MEMBER, snapshot.memberName)
            .putString(KEY_QUESTION, snapshot.lastQuestion)
            .apply()
    }

    private companion object {
        const val PREF_NAME = "agent_memory"
        const val KEY_MEDICINE = "last_medicine"
        const val KEY_INGREDIENT = "last_ingredient"
        const val KEY_MEMBER = "last_member"
        const val KEY_QUESTION = "last_question"
    }
}
