package com.claude.codex.ai.monitoring.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.claude.codex.ai.monitoring.desktop.resources.Res
import com.claude.codex.ai.monitoring.desktop.resources.inter_medium
import com.claude.codex.ai.monitoring.desktop.resources.inter_regular
import com.claude.codex.ai.monitoring.desktop.resources.inter_semibold
import com.claude.codex.ai.monitoring.desktop.resources.jetbrains_mono_regular
import com.claude.codex.ai.monitoring.desktop.resources.space_grotesk_bold
import com.claude.codex.ai.monitoring.desktop.resources.space_grotesk_semibold
import org.jetbrains.compose.resources.Font

// Same design tokens as the phone (design/tokens.md), dark-first like the mobile app.
val Color0F1A = Color(0xFF0B0F1A)
val Color1826 = Color(0xFF121826)
val Color2236 = Color(0xFF1A2236)
val Color304A = Color(0xFF26304A)
val ColorEAF5 = Color(0xFFE6EAF5)
val ColorA3B8 = Color(0xFF94A3B8)
val Color5CFF = Color(0xFF7C5CFF)
val ColorD3EE = Color(0xFF22D3EE)
val ColorBF24 = Color(0xFFFBBF24)
val ColorD399 = Color(0xFF34D399)
val Color7171 = Color(0xFFF87171)
val ColorFFFF = Color(0xFFFFFFFF)

@Immutable
data class DesktopColors(
    val background: Color = Color0F1A,
    val surface: Color = Color1826,
    val surfaceElevated: Color = Color2236,
    val outline: Color = Color304A,
    val textPrimary: Color = ColorEAF5,
    val textSecondary: Color = ColorA3B8,
    val brandStart: Color = Color5CFF,
    val brandEnd: Color = ColorD3EE,
    val onBrand: Color = ColorFFFF,
    val running: Color = ColorD3EE,
    val waiting: Color = ColorBF24,
    val done: Color = ColorD399,
    val error: Color = Color7171,
    val stale: Color = ColorA3B8,
) {
    val brandGradient: Brush get() = Brush.linearGradient(listOf(brandStart, brandEnd))
}

val LocalDesktopColors = staticCompositionLocalOf { DesktopColors() }

object DesktopTheme {
    val colors: DesktopColors
        @Composable get() = LocalDesktopColors.current
}

@Composable
fun DesktopTheme(content: @Composable () -> Unit) {
    val colors = DesktopColors()
    val grotesk = FontFamily(
        Font(Res.font.space_grotesk_semibold, FontWeight.SemiBold),
        Font(Res.font.space_grotesk_bold, FontWeight.Bold),
    )
    val inter = FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_medium, FontWeight.Medium),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
    )
    val mono = FontFamily(Font(Res.font.jetbrains_mono_regular, FontWeight.Normal))
    val typography = Typography(
        headlineMedium = TextStyle(fontFamily = grotesk, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
        titleLarge = TextStyle(fontFamily = grotesk, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
        titleMedium = TextStyle(fontFamily = grotesk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
        bodyMedium = TextStyle(fontFamily = inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
        bodySmall = TextStyle(fontFamily = inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
        labelMedium = TextStyle(fontFamily = inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontFamily = inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp),
        bodyLarge = TextStyle(fontFamily = mono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    )
    CompositionLocalProvider(LocalDesktopColors provides colors) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = colors.brandStart,
                background = colors.background,
                surface = colors.surface,
                onSurface = colors.textPrimary,
                outline = colors.outline,
            ),
            typography = typography,
            content = content,
        )
    }
}
