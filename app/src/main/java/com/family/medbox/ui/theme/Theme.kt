package com.family.medbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private fun lightScheme() = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Accent,
    onSecondary = OnPrimary,
    secondaryContainer = AccentContainer,
    onSecondaryContainer = OnAccentContainer,
    tertiary = PrimaryLight,
    error = Danger,
    onError = OnPrimary,
    errorContainer = DangerContainer,
    onErrorContainer = Color(0xFF5A1A14),
    background = Background,
    onBackground = Ink,
    surface = SurfaceWhite,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1F4F2),
    onSurfaceVariant = InkSecondary,
    outline = Outline,
    outlineVariant = Divider
)

private fun darkScheme() = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Accent,
    onSecondary = OnPrimary,
    secondaryContainer = AccentContainer,
    onSecondaryContainer = OnAccentContainer,
    tertiary = PrimaryLight,
    error = Danger,
    onError = OnPrimary,
    errorContainer = DangerContainer,
    onErrorContainer = Color(0xFFFFD9D4),
    background = Background,
    onBackground = Ink,
    surface = SurfaceWhite,
    onSurface = Ink,
    surfaceVariant = Color(0xFF232D29),
    onSurfaceVariant = InkSecondary,
    outline = Outline,
    outlineVariant = Divider
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MediBoxTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    // 在子内容组合之前写入，颜色 getter 第一帧就能读到正确值，避免闪一下浅色
    remember(dark) {
        ThemeState.dark = dark
        dark
    }
    val colorScheme = remember(dark) { if (dark) darkScheme() else lightScheme() }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
