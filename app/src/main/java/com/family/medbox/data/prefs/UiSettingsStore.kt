package com.family.medbox.data.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 界面偏好（长辈模式等）：只影响显示方式，不触碰药品数据。
 */
class UiSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _elderMode = MutableStateFlow(prefs.getBoolean(KEY_ELDER_MODE, false))
    val elderMode: StateFlow<Boolean> = _elderMode.asStateFlow()

    /** 长辈模式下的字号放大倍数，默认 1.2 倍。 */
    private val _elderFontScale = MutableStateFlow(prefs.getFloat(KEY_FONT_SCALE, 1.2f))
    val elderFontScale: StateFlow<Float> = _elderFontScale.asStateFlow()

    /** 对比度增强：加深次要文字与分隔线。 */
    private val _highContrast = MutableStateFlow(prefs.getBoolean(KEY_HIGH_CONTRAST, false))
    val highContrast: StateFlow<Boolean> = _highContrast.asStateFlow()

    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                KEY_ELDER_MODE -> _elderMode.value = prefs.getBoolean(KEY_ELDER_MODE, false)
                KEY_FONT_SCALE -> _elderFontScale.value = prefs.getFloat(KEY_FONT_SCALE, 1.2f)
                KEY_HIGH_CONTRAST -> _highContrast.value = prefs.getBoolean(KEY_HIGH_CONTRAST, false)
            }
        }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    fun setElderMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ELDER_MODE, enabled).apply()
        _elderMode.value = enabled
    }

    fun setElderFontScale(scale: Float) {
        prefs.edit().putFloat(KEY_FONT_SCALE, scale).apply()
        _elderFontScale.value = scale
    }

    fun setHighContrast(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply()
        _highContrast.value = enabled
    }

    private companion object {
        const val PREF_NAME = "medibox_ui"
        const val KEY_ELDER_MODE = "elder_mode"
        const val KEY_FONT_SCALE = "elder_font_scale"
        const val KEY_HIGH_CONTRAST = "high_contrast"
    }
}
