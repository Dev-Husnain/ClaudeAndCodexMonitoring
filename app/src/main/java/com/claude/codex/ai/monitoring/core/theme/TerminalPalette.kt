package com.claude.codex.ai.monitoring.core.theme

import androidx.compose.ui.graphics.Color
import com.claude.codex.ai.monitoring.domain.models.TerminalColor

/**
 * Colours for the mirrored terminal. It is always drawn dark, like the terminal Claude Code runs in,
 * so its own colour choices stay readable in both app themes.
 */
object TerminalPalette {
    val Background = Color0F1A
    val Foreground = ColorEAF5

    // ANSI 0..15 (normal, then bright), tuned to the app's dark palette.
    private val ansi = listOf(
        Color(0xFF1A2236), Color(0xFFF87171), Color(0xFF34D399), Color(0xFFFBBF24),
        Color(0xFF60A5FA), Color(0xFFC084FC), Color(0xFF22D3EE), Color(0xFFCBD5E1),
        Color(0xFF64748B), Color(0xFFFCA5A5), Color(0xFF6EE7B7), Color(0xFFFDE68A),
        Color(0xFF93C5FD), Color(0xFFD8B4FE), Color(0xFF67E8F9), Color(0xFFF8FAFC),
    )

    fun color(color: TerminalColor): Color = when (color) {
        is TerminalColor.Rgb -> Color(OPAQUE or color.rgb)
        is TerminalColor.Palette -> palette(color.index)
    }

    /** The xterm 256-colour palette: 16 theme colours, a 6×6×6 cube, then 24 greys. */
    private fun palette(index: Int): Color = when (index) {
        in 0..15 -> ansi[index]
        in CUBE_START until GREY_START -> {
            val i = index - CUBE_START
            Color(OPAQUE or (level(i / 36) shl 16) or (level(i / 6 % 6) shl 8) or level(i % 6))
        }
        in GREY_START..255 -> (8 + (index - GREY_START) * 10).let { Color(OPAQUE or (it shl 16) or (it shl 8) or it) }
        else -> Foreground
    }

    private fun level(step: Int) = if (step == 0) 0 else 55 + step * 40

    private const val OPAQUE = 0xFF shl 24
    private const val CUBE_START = 16
    private const val GREY_START = 232
}
