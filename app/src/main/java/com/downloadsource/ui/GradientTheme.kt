package com.downloadsource.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.downloadsource.ThemeStore

data class GradientPalette(
    val primary: List<Color>,
    val accent: List<Color>,
    val background: List<Color>,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceDim: Color,
)

val LightPalette = GradientPalette(
    primary = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFEC4899)),
    accent = listOf(Color(0xFF06B6D4), Color(0xFF3B82F6)),
    background = listOf(Color(0xFFF5F3FF), Color(0xFFEEF2FF), Color(0xFFFFF1F2)),
    surface = Color(0xCCFFFFFF),
    onSurface = Color(0xFF1E293B),
    onSurfaceDim = Color(0xFF64748B),
)

val DarkPalette = GradientPalette(
    primary = listOf(Color(0xFF818CF8), Color(0xFFA78BFA), Color(0xFFF472B6)),
    accent = listOf(Color(0xFF22D3EE), Color(0xFF60A5FA)),
    background = listOf(Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF24243E)),
    surface = Color(0x22FFFFFF),
    onSurface = Color(0xFFF1F5F9),
    onSurfaceDim = Color(0xFF94A3B8),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

@Composable
fun rememberDark(): Boolean {
    val ctx = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    return when (ThemeStore.get(ctx)) {
        ThemeStore.LIGHT -> false
        ThemeStore.DARK -> true
        else -> systemDark
    }
}

@Composable
fun GradientBackground(palette: GradientPalette, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides palette) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(palette.background, Offset.Zero, Offset.Infinite)
            )
        ) { content() }
    }
}

fun GradientPalette.primaryBrush() = Brush.linearGradient(primary)
fun GradientPalette.accentBrush() = Brush.linearGradient(accent)
