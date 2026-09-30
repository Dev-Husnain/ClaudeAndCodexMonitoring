package com.claude.codex.ai.monitoring.domain.models

/** What a wrapper session's terminal shows: its newest lines, oldest first. */
data class TerminalScreenModel(
    val columns: Int,
    val lines: List<TerminalLineModel>,
)

data class TerminalLineModel(val spans: List<TerminalSpanModel>)

data class TerminalSpanModel(
    val text: String,
    val foreground: TerminalColor? = null,
    val background: TerminalColor? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val dim: Boolean = false,
    val inverse: Boolean = false,
)

/** A terminal colour; `null` elsewhere means the terminal's default. */
sealed interface TerminalColor {
    /** xterm palette entry 0..255; 0..15 are the theme's ANSI colours. */
    data class Palette(val index: Int) : TerminalColor

    data class Rgb(val rgb: Int) : TerminalColor
}

/** Keys on the phone's terminal keys row. */
enum class TerminalKeyType { ENTER, ESCAPE, TAB, SHIFT_TAB, UP, DOWN, CTRL_C, DIGIT_1, DIGIT_2, DIGIT_3 }
