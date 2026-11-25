package com.family.medbox.data.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 采购清单里的一项：只记药名和加入原因，买回后整单核销。 */
data class ShoppingItem(
    val name: String,
    val reason: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

/** 家庭购物清单：轻量 JSON 存在 SharedPreferences，跨页面共享一份。 */
class ShoppingStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _items = MutableStateFlow(read())
    val items: StateFlow<List<ShoppingItem>> = _items.asStateFlow()

    // SharedPreferences 内部用弱引用保存监听器，必须持有强引用。
    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_ITEMS) _items.value = read()
        }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun addAll(names: List<String>, reason: String = "智能体建议") {
        if (names.isEmpty()) return
        val merged = _items.value.toMutableList()
        names.forEach { raw ->
            val name = raw.trim()
            if (name.isBlank() || merged.any { it.name == name }) return@forEach
            merged += ShoppingItem(name = name, reason = reason)
        }
        _items.value = merged
        write()
    }

    /** 从备份恢复采购清单。 */
    fun replaceAll(items: List<ShoppingItem>) {
        _items.value = items
        write()
    }

    fun clear() {
        _items.value = emptyList()
        prefs.edit().remove(KEY_ITEMS).apply()
    }

    private fun read(): List<ShoppingItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        ShoppingItem(
                            name = item.getString("name"),
                            reason = item.optString("reason", ""),
                            addedAt = item.optLong("addedAt", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun write() {
        val array = JSONArray()
        _items.value.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("name", item.name)
                    put("reason", item.reason)
                    put("addedAt", item.addedAt)
                }
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    companion object {
        private const val PREF_NAME = "medibox_shopping"
        private const val KEY_ITEMS = "items"
    }
}
