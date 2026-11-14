package com.downloadsource.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.downloadsource.ThemeStore

data class GradientPalette(val primary: List<Color>, val accent: List<Color>, val background: List<Color>, val surface: Color, val onSurface: Color, val onSurfaceDim: Color)

val LightPalette = GradientPalette(
    primary = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)), 
    accent = listOf(Color(0xFF06B6D4)),
    background = listOf(Color(0xFFF8FAFC), Color(0xFFE2E8F0)), // 高级浅灰蓝
    surface = Color(0xE6FFFFFF), 
    onSurface = Color(0xFF1E293B), 
    onSurfaceDim = Color(0xFF64748B)
)
val DarkPalette = GradientPalette(
    primary = listOf(Color(0xFF818CF8), Color(0xFFA78BFA)), 
    accent = listOf(Color(0xFF22D3EE)),
    background = listOf(Color(0xFF0F172A), Color(0xFF1E293B)), 
    surface = Color(0x80000000), 
    onSurface = Color(0xFFF1F5F9), 
    onSurfaceDim = Color(0xFF94A3B8)
)

// 改掉难看的粉白，换成日落珊瑚红
val CoralLightPalette = LightPalette.copy(primary = listOf(Color(0xFFFF7E5F), Color(0xFFFEB47B)), background = listOf(Color(0xFFFFF7F5), Color(0xFFFFF0EC)))
val CoralDarkPalette = DarkPalette.copy(primary = listOf(Color(0xFFFF9A8B), Color(0xFFFF6A88)), background = listOf(Color(0xFF2D1B1B), Color(0xFF1A1111)))

val ForestLightPalette = LightPalette.copy(primary = listOf(Color(0xFF10B981), Color(0xFF059669)), background = listOf(Color(0xFFF0FDF4), Color(0xFFDCFCE7)))
val SunsetLightPalette = LightPalette.copy(primary = listOf(Color(0xFFF59E0B), Color(0xFFEF4444)), background = listOf(Color(0xFFFFFBEB), Color(0xFFFEF3C7)))
val OceanLightPalette = LightPalette.copy(primary = listOf(Color(0xFF0EA5E9), Color(0xFF3B82F6)), background = listOf(Color(0xFFF0F9FF), Color(0xFFE0F2FE)))
val CyberLightPalette = LightPalette.copy(primary = listOf(Color(0xFFD946EF), Color(0xFF8B5CF6)), background = listOf(Color(0xFFFDF4FF), Color(0xFFFAE8FF)))

val ForestDarkPalette = DarkPalette.copy(primary = listOf(Color(0xFF34D399), Color(0xFF10B981)), background = listOf(Color(0xFF022C22), Color(0xFF064E3B)))
val SunsetDarkPalette = DarkPalette.copy(primary = listOf(Color(0xFFFBBF24), Color(0xFFF87171)), background = listOf(Color(0xFF451A03), Color(0xFF7F1D1D)))
val OceanDarkPalette = DarkPalette.copy(primary = listOf(Color(0xFF38BDF8), Color(0xFF60A5FA)), background = listOf(Color(0xFF082F49), Color(0xFF1E3A8A)))
val CyberDarkPalette = DarkPalette.copy(primary = listOf(Color(0xFFF0ABFC), Color(0xFFC084FC)), background = listOf(Color(0xFF2E1065), Color(0xFF4C1D95)))

val LocalPalette = staticCompositionLocalOf { LightPalette }

@Composable
fun rememberPalette(): GradientPalette {
    val ctx = LocalContext.current
    val isDark = when (ThemeStore.get(ctx)) { ThemeStore.LIGHT -> false; ThemeStore.DARK -> true; else -> isSystemInDarkTheme() }
    return when (ThemeStore.getThemeStyle(ctx)) {
        1 -> if (isDark) CoralDarkPalette else CoralLightPalette
        2 -> if (isDark) ForestDarkPalette else ForestLightPalette
        3 -> if (isDark) SunsetDarkPalette else SunsetLightPalette
        4 -> if (isDark) OceanDarkPalette else OceanLightPalette
        5 -> if (isDark) CyberDarkPalette else CyberLightPalette
        else -> if (isDark) DarkPalette else LightPalette
    }
}

val SafeTypography = Typography(
    displayLarge = Typography().displayLarge.copy(fontFamily = FontFamily.SansSerif),
    displayMedium = Typography().displayMedium.copy(fontFamily = FontFamily.SansSerif),
    displaySmall = Typography().displaySmall.copy(fontFamily = FontFamily.SansSerif),
    headlineLarge = Typography().headlineLarge.copy(fontFamily = FontFamily.SansSerif),
    headlineMedium = Typography().headlineMedium.copy(fontFamily = FontFamily.SansSerif),
    headlineSmall = Typography().headlineSmall.copy(fontFamily = FontFamily.SansSerif),
    titleLarge = Typography().titleLarge.copy(fontFamily = FontFamily.SansSerif),
    titleMedium = Typography().titleMedium.copy(fontFamily = FontFamily.SansSerif),
    titleSmall = Typography().titleSmall.copy(fontFamily = FontFamily.SansSerif),
    bodyLarge = Typography().bodyLarge.copy(fontFamily = FontFamily.SansSerif),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = FontFamily.SansSerif),
    bodySmall = Typography().bodySmall.copy(fontFamily = FontFamily.SansSerif),
    labelLarge = Typography().labelLarge.copy(fontFamily = FontFamily.SansSerif),
    labelMedium = Typography().labelMedium.copy(fontFamily = FontFamily.SansSerif),
    labelSmall = Typography().labelSmall.copy(fontFamily = FontFamily.SansSerif)
)

@Composable
fun GradientBackground(palette: GradientPalette, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(typography = SafeTypography) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(palette.background, Offset.Zero, Offset.Infinite))) { content() }
        }
    }
}
