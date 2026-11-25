package com.family.medbox.data.prefs

import android.content.Context
import com.family.medbox.data.model.Medicine
import com.family.medbox.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.temporal.ChronoUnit

/** 开封后可用天数：只对确实有「开封效期」的剂型计算。 */
object OpenedShelfLife {

    fun shelfDays(medicine: Medicine): Int? {
        val text = (medicine.name + " " + medicine.form + " " + medicine.spec).lowercase()
        return when {
            text.contains("眼药水") || text.contains("滴眼液") ||
                text.contains("眼膏") || text.contains("滴耳液") -> 28
            text.contains("胰岛素") -> 28
            text.contains("糖浆") || text.contains("口服液") ||
                text.contains("混悬液") || text.contains("合剂") -> 30
            text.contains("喷雾") || text.contains("鼻喷") || text.contains("吸入") -> 30
            text.contains("乳膏") || text.contains("软膏") ||
                text.contains("凝胶") || text.contains("药膏") -> 90
            else -> null
        }
    }

    /** 开封后的实际可用截止日：取「整盒有效期」和「开封效期」里更早的那个。 */
    fun expireDate(medicine: Medicine, openedIso: String): String? {
        val days = shelfDays(medicine) ?: return null
        val opened = DateUtils.parseIso(openedIso) ?: return null
        val fromOpened = DateUtils.toIso(opened.plusDays(days.toLong()))
        val fromOpenedDate = DateUtils.parseIso(fromOpened) ?: return fromOpened
        val box = DateUtils.parseIso(medicine.expiryDate)
        return if (box != null && box.isBefore(fromOpenedDate)) {
            DateUtils.toIso(box)
        } else {
            fromOpened
        }
    }

    fun usedDays(openedIso: String): Long {
        val opened = DateUtils.parseIso(openedIso) ?: return 0L
        return ChronoUnit.DAYS.between(opened, DateUtils.today()).coerceAtLeast(0L)
    }

    fun daysLeft(medicine: Medicine, openedIso: String): Long? =
        DateUtils.daysLeft(expireDate(medicine, openedIso))
}

/** 记录每盒药哪天开封，按药品 id 存本机。 */
class OpenedStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _opened = MutableStateFlow(loadAll())
    val opened: StateFlow<Map<Long, String>> = _opened.asStateFlow()

    fun markOpened(medicineId: Long, iso: String = DateUtils.toIso(DateUtils.today())) {
        val next = _opened.value.toMutableMap()
        next[medicineId] = iso
        prefs.edit().putString(KEY_PREFIX + medicineId, iso).apply()
        _opened.value = next
    }

    fun clear(medicineId: Long) {
        val next = _opened.value.toMutableMap()
        next.remove(medicineId)
        prefs.edit().remove(KEY_PREFIX + medicineId).apply()
        _opened.value = next
    }

    private fun loadAll(): Map<Long, String> = buildMap {
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(KEY_PREFIX) && value is String) {
                val id = key.removePrefix(KEY_PREFIX).toLongOrNull() ?: return@forEach
                put(id, value)
            }
        }
    }

    private companion object {
        const val PREF_NAME = "medibox_opened"
        const val KEY_PREFIX = "med_"
    }
}
