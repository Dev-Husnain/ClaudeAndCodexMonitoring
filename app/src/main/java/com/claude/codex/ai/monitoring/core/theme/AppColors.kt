package com.claude.codex.ai.monitoring.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Semantic colour roles beyond the Material scheme: layered surfaces, brand gradient and session status. */
@Immutable
data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val brandStart: Color,
    val brandEnd: Color,
    val onBrand: Color,
    val running: Color,
    val waiting: Color,
    val done: Color,
    val error: Color,
    val stale: Color,
    val isDark: Boolean,
) {
    val brandGradient: Brush get() = Brush.linearGradient(listOf(brandStart, brandEnd))
}

val DarkAppColors = AppColors(
    background = Color0F1A,
    surface = Color1826,
    surfaceElevated = Color2236,
    outline = Color304A,
    textPrimary = ColorEAF5,
    textSecondary = ColorA3B8,
    brandStart = Color5CFF,
    brandEnd = ColorD3EE,
    onBrand = ColorFFFF,
    running = ColorD3EE,
    waiting = ColorBF24,
    done = ColorD399,
    error = Color7171,
    stale = ColorA3B8,
    isDark = true,
)

val LightAppColors = AppColors(
    background = ColorF7FB,
    surface = ColorFFFF,
    surfaceElevated = ColorF1F8,
    outline = ColorDEEA,
    textPrimary = Color0F1A,
    textSecondary = Color6478,
    brandStart = Color4AFF,
    brandEnd = Color91B2,
    onBrand = ColorFFFF,
    running = Color91B2,
    waiting = Color7706,
    done = Color9669,
    error = Color2626,
    stale = Color748B,
    isDark = false,
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }
