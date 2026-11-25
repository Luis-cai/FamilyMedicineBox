package com.family.medbox.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 分装进度：记录哪些日期已经按周分好药。
 * 只存日期，不涉及药品数据和家庭同步。
 */
class PackingStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _packedDays = MutableStateFlow(load())
    val packedDays: StateFlow<Set<String>> = _packedDays.asStateFlow()

    fun toggle(dateIso: String) {
        val next = _packedDays.value.toMutableSet()
        if (!next.add(dateIso)) next.remove(dateIso)
        prefs.edit().putStringSet(KEY_PACKED_DAYS, next).apply()
        _packedDays.value = next
    }

    private fun load(): Set<String> =
        prefs.getStringSet(KEY_PACKED_DAYS, emptySet()).orEmpty().toSet()

    private companion object {
        const val PREF_NAME = "medibox_packing"
        const val KEY_PACKED_DAYS = "packed_days"
    }
}
