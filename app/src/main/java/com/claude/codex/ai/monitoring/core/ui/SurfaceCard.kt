package com.claude.codex.ai.monitoring.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens

/**
 * Layered card: surface colour, a thin outline that fades from top to bottom, and optionally a
 * tint of [accent] at the top edge (used to make "Needs you" cards glow).
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(Dimens.SpaceLg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val border = BorderStroke(
        width = Dimens.BorderThin,
        brush = Brush.verticalGradient(
            listOf(
                accent?.copy(alpha = 0.7f) ?: colors.outline,
                colors.outline.copy(alpha = 0.35f),
            ),
        ),
    )
    val cardContent: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = colors.surface,
            border = border,
            content = cardContent,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = colors.surface,
            border = border,
            content = cardContent,
        )
    }
}

@Preview
@Composable
private fun SurfaceCardPreview() {
    AppTheme(darkTheme = true) {
        SurfaceCard(accent = AppTheme.colors.waiting) {
            Text("Card content", color = AppTheme.colors.textPrimary)
        }
    }
}
