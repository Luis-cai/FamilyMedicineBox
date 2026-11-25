package com.family.medbox.ui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

/**
 * 全局明暗状态。MediBoxTheme 每帧根据系统设置写入，下面的颜色 getter 读取它，
 * 因此切换深色模式时整棵树会正常重组，调用方不用改任何代码。
 */
object ThemeState {
    private val darkState = mutableStateOf(false)

    var dark: Boolean
        get() = darkState.value
        set(value) {
            darkState.value = value
        }

    val isDark: Boolean get() = darkState.value
}

/** 主色：沉稳的青绿色，传达"健康 / 可信赖"的医疗感 */
val Primary: Color get() = if (ThemeState.isDark) Color(0xFF8FD9C4) else Color(0xFF2F6F63)
val PrimaryDark: Color get() = if (ThemeState.isDark) Color(0xFF6FBFA9) else Color(0xFF245851)
val PrimaryLight: Color get() = if (ThemeState.isDark) Color(0xFFB4EBD9) else Color(0xFF4E9184)
val PrimaryContainer: Color get() = if (ThemeState.isDark) Color(0xFF1C3B33) else Color(0xFFDDEDE8)
val OnPrimaryContainer: Color get() = if (ThemeState.isDark) Color(0xFFCFEDE2) else Color(0xFF12332D)

/** 主色上的前景色：浅色模式是白色，深色模式的主色变亮，文字需要用深色 */
val OnPrimary: Color get() = if (ThemeState.isDark) Color(0xFF0B2A22) else Color(0xFFFFFFFF)

/** 强调色：温暖的琥珀色，用于提醒、预警 */
val Accent: Color get() = if (ThemeState.isDark) Color(0xFFF0B45C) else Color(0xFFE9A23B)
/** 琥珀色在浅色底上的文字 / 图标版本，保证对比度 ≥ 4.5:1 */
val AccentInk: Color get() = if (ThemeState.isDark) Color(0xFFF3C57C) else Color(0xFF8A6418)
val AccentContainer: Color get() = if (ThemeState.isDark) Color(0xFF3A2C16) else Color(0xFFFCEFD9)
val OnAccentContainer: Color get() = if (ThemeState.isDark) Color(0xFFF7DCAE) else Color(0xFF5E4110)

/** 语义色 */
val Danger: Color get() = if (ThemeState.isDark) Color(0xFFF2938A) else Color(0xFFB03A31)
val DangerContainer: Color get() = if (ThemeState.isDark) Color(0xFF3B211E) else Color(0xFFFBE4E1)
val Success: Color get() = if (ThemeState.isDark) Color(0xFF82D3A3) else Color(0xFF337A4B)
val SuccessContainer: Color get() = if (ThemeState.isDark) Color(0xFF1B3526) else Color(0xFFE1F2E6)

/** 中性色 */
val Ink: Color get() = if (ThemeState.isDark) Color(0xFFE9F2EE) else Color(0xFF1B2B27)
val InkSecondary: Color get() = if (ThemeState.isDark) Color(0xFFA9B9B2) else Color(0xFF5C6B66)

/**
 * 夜间 / 长辈模式下的中性色。对比度增强开关打开后，
 * 次要文字、分隔线与描边会整体加深，背景提亮到纯白。
 */
object ContrastPalette {

    private val enabledState = mutableStateOf(false)

    var enabled: Boolean
        get() = enabledState.value
        set(value) {
            enabledState.value = value
        }

    val muted: Color
        get() = when {
            ThemeState.isDark -> Color(0xFF93A49D)
            enabled -> Color(0xFF34403B)
            else -> Color(0xFF67746E)
        }

    val divider: Color
        get() = when {
            ThemeState.isDark -> Color(0xFF2B3632)
            enabled -> Color(0xFFB4C0BA)
            else -> Color(0xFFEEF1EE)
        }

    val outline: Color
        get() = when {
            ThemeState.isDark -> Color(0xFF3A4742)
            enabled -> Color(0xFF93A39C)
            else -> Color(0xFFE3E8E5)
        }

    val background: Color
        get() = when {
            ThemeState.isDark -> Color(0xFF0F1513)
            enabled -> Color(0xFFFFFFFF)
            else -> Color(0xFFF6F7F5)
        }
}

val InkMuted: Color get() = ContrastPalette.muted
val Background: Color get() = ContrastPalette.background

/** 卡片 / 输入框等表面的底色 */
val SurfaceWhite: Color get() = if (ThemeState.isDark) Color(0xFF19211E) else Color(0xFFFFFFFF)

/** 页面顶部渐晕的起始色：深色下换成深墨绿，避免顶部出现发白的一条 */
val HeroTint: Color get() = if (ThemeState.isDark) Color(0xFF16241F) else Color(0xFFEAF4F0)

val Outline: Color get() = ContrastPalette.outline
val Divider: Color get() = ContrastPalette.divider

/** 成员头像色板 */
val MemberPalette: List<Color>
    get() = if (ThemeState.isDark) {
        listOf(
            Color(0xFF63C3AC),
            Color(0xFFE4807A),
            Color(0xFFEFB55F),
            Color(0xFF7FA9DA),
            Color(0xFFB295D0),
            Color(0xFF6FBF8A)
        )
    } else {
        listOf(
            Color(0xFF2F6F63),
            Color(0xFFD2544A),
            Color(0xFFE9A23B),
            Color(0xFF4A7DB5),
            Color(0xFF8A6BB0),
            Color(0xFF3E8E5A)
        )
    }
