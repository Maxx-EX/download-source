package com.downloadsource.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.downloadsource.ThemeStore

data class GradientPalette(val primary: List<Color>, val accent: List<Color>, val background: List<Color>, val surface: Color, val onSurface: Color, val onSurfaceDim: Color)

val LightPalette = GradientPalette(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFEC4899)), listOf(Color(0xFF06B6D4), Color(0xFF3B82F6)), listOf(Color(0xFFF5F3FF), Color(0xFFEEF2FF), Color(0xFFFFF1F2)), Color(0xCCFFFFFF), Color(0xFF1E293B), Color(0xFF64748B))
val DarkPalette = GradientPalette(listOf(Color(0xFF818CF8), Color(0xFFA78BFA), Color(0xFFF472B6)), listOf(Color(0xFF22D3EE), Color(0xFF60A5FA)), listOf(Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF24243E)), Color(0x22FFFFFF), Color(0xFFF1F5F9), Color(0xFF94A3B8))

val ForestLightPalette = LightPalette.copy(primary = listOf(Color(0xFF10B981), Color(0xFF059669)), background = listOf(Color(0xFFECFDF5), Color(0xFFF0FDF4)))
val SunsetLightPalette = LightPalette.copy(primary = listOf(Color(0xFFF59E0B), Color(0xFFEF4444)), background = listOf(Color(0xFFFFFBEB), Color(0xFFFEF2F2)))
val OceanLightPalette = LightPalette.copy(primary = listOf(Color(0xFF0EA5E9), Color(0xFF3B82F6)), background = listOf(Color(0xFFF0F9FF), Color(0xFFEFF6FF)))
val CyberLightPalette = LightPalette.copy(primary = listOf(Color(0xFFD946EF), Color(0xFF8B5CF6)), background = listOf(Color(0xFFFAF5FF), Color(0xFFF3E8FF)))

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
        1 -> if (isDark) ForestDarkPalette else ForestLightPalette
        2 -> if (isDark) SunsetDarkPalette else SunsetLightPalette
        3 -> if (isDark) OceanDarkPalette else OceanLightPalette
        4 -> if (isDark) CyberDarkPalette else CyberLightPalette
        else -> if (isDark) DarkPalette else LightPalette
    }
}

@Composable
fun GradientBackground(palette: GradientPalette, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides palette) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(palette.background, Offset.Zero, Offset.Infinite))) { content() }
    }
}
