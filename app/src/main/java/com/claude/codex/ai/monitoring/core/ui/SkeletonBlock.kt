package com.claude.codex.ai.monitoring.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.theme.LocalReduceMotion

/** Shimmering placeholder block; size it with [modifier]. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraSmall,
) {
    val colors = AppTheme.colors
    val sweepPx = with(LocalDensity.current) { Dimens.GlowRadius.toPx() }
    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SWEEP_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    val offset = if (LocalReduceMotion.current) 0f else progress * sweepPx * 2 - sweepPx
    Box(
        modifier = modifier.background(
            brush = Brush.linearGradient(
                colors = listOf(colors.surfaceElevated, colors.outline.copy(alpha = 0.6f), colors.surfaceElevated),
                start = Offset(offset, 0f),
                end = Offset(offset + sweepPx, 0f),
            ),
            shape = shape,
        ),
    )
}

private const val SWEEP_MS = 1_300

@Preview
@Composable
private fun SkeletonBlockPreview() {
    AppTheme(darkTheme = true) {
        SkeletonBlock(modifier = Modifier.fillMaxWidth().height(Dimens.SkeletonLine))
    }
}
