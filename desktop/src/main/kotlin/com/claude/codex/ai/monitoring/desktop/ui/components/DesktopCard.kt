package com.claude.codex.ai.monitoring.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.claude.codex.ai.monitoring.desktop.ui.theme.DesktopTheme

/** Layered surface card matching the phone's SurfaceCard. */
@Composable
fun DesktopCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = DesktopTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .background(colors.surface, shape)
            .border(
                1.dp,
                Brush.verticalGradient(listOf(accent?.copy(alpha = 0.7f) ?: colors.outline, colors.outline.copy(alpha = 0.35f))),
                shape,
            )
            .padding(16.dp),
        content = content,
    )
}
