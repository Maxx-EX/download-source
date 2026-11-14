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

// 雾霾蓝 / 优雅灰
val LightPalette = GradientPalette(
    primary = listOf(Color(0xFF5B8DEF), Color(0xFF7AA2F7)), 
    accent = listOf(Color(0xFF4ECDC4)),
    background = listOf(Color(0xFFF4F7FB), Color(0xFFE6EBF5)), 
    surface = Color(0xE6FFFFFF), 
    onSurface = Color(0xFF2D3748), 
    onSurfaceDim = Color(0xFF718096)
)
// 赛博暗夜
val DarkPalette = GradientPalette(
    primary = listOf(Color(0xFF66B2FF), Color(0xFF3399FF)), 
    accent = listOf(Color(0xFF00FFCC)),
    background = listOf(Color(0xFF121826), Color(0xFF1A2235)), 
    surface = Color(0x80000000), 
    onSurface = Color(0xFFE2E8F0), 
    onSurfaceDim = Color(0xFFA0AEC0)
)

// 玫瑰金
val RoseLightPalette = LightPalette.copy(primary = listOf(Color(0xFFF08A8A), Color(0xFFFFADAD)), background = listOf(Color(0xFFFCF5F5), Color(0xFFF7E8E8)))
val RoseDarkPalette = DarkPalette.copy(primary = listOf(Color(0xFFFF9A9A), Color(0xFFFF7A7A)), background = listOf(Color(0xFF2D1F1F), Color(0xFF1F1515)))

// 极光绿
val ForestLightPalette = LightPalette.copy(primary = listOf(Color(0xFF48BB78), Color(0xFF68D391)), background = listOf(Color(0xFFF0FDF4), Color(0xFFDCFCE7)))
val ForestDarkPalette = DarkPalette.copy(primary = listOf(Color(0xFF68D391), Color(0xFF48BB78)), background = listOf(Color(0xFF022C22), Color(0xFF064E3B)))

val LocalPalette = staticCompositionLocalOf { LightPalette }

@Composable
fun rememberPalette(): GradientPalette {
    val ctx = LocalContext.current
    val isDark = when (ThemeStore.get(ctx)) { ThemeStore.LIGHT -> false; ThemeStore.DARK -> true; else -> isSystemInDarkTheme() }
    return when (ThemeStore.getThemeStyle(ctx)) {
        1 -> if (isDark) RoseDarkPalette else RoseLightPalette
        2 -> if (isDark) ForestDarkPalette else ForestLightPalette
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
