package com.claude.codex.ai.monitoring.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens

/**
 * Screen background: the theme background with two soft brand-coloured glows. It is drawn
 * edge-to-edge, behind the status and navigation bars, so the bars always match the theme.
 * Screens apply their own insets to the content inside it.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val glowAlpha = if (colors.isDark) 0.22f else 0.12f
    val glowRadius = with(LocalDensity.current) { Dimens.GlowRadius.toPx() }
    Box(
        modifier = modifier
            .background(colors.background)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(colors.brandStart.copy(alpha = glowAlpha), colors.brandStart.copy(alpha = 0f)),
                        center = Offset(size.width * 0.1f, 0f),
                        radius = glowRadius,
                    ),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(colors.brandEnd.copy(alpha = glowAlpha * 0.7f), colors.brandEnd.copy(alpha = 0f)),
                        center = Offset(size.width, size.height * 0.35f),
                        radius = glowRadius,
                    ),
                )
            },
        content = content,
    )
}

@Preview
@Composable
private fun AuroraBackgroundPreview() {
    AppTheme(darkTheme = true) {
        AuroraBackground(modifier = Modifier.fillMaxSize()) { }
    }
}
