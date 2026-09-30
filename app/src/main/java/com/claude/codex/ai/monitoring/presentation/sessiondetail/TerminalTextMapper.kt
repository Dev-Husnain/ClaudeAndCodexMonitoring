package com.claude.codex.ai.monitoring.presentation.sessiondetail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.claude.codex.ai.monitoring.core.theme.TerminalPalette
import com.claude.codex.ai.monitoring.domain.models.TerminalLineModel

private const val DIM_ALPHA = 0.6f

/** One terminal line as styled text; default colours are left to the surrounding text style. */
fun TerminalLineModel.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        var foreground = span.foreground?.let(TerminalPalette::color)
        var background = span.background?.let(TerminalPalette::color)
        if (span.inverse) {
            val swapped = background ?: TerminalPalette.Background
            background = foreground ?: TerminalPalette.Foreground
            foreground = swapped
        }
        if (span.dim) foreground = (foreground ?: TerminalPalette.Foreground).copy(alpha = DIM_ALPHA)
        withStyle(
            SpanStyle(
                color = foreground ?: Color.Unspecified,
                background = background ?: Color.Unspecified,
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                textDecoration = if (span.underline) TextDecoration.Underline else null,
            ),
        ) { append(span.text) }
    }
}
