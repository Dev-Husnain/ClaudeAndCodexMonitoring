package com.claude.codex.ai.monitoring.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.theme.LocalReduceMotion

/**
 * Animated status dot: a soft pulse ring while running, a breathing shimmer while waiting for
 * input and a static glow otherwise. Motion is disabled when the system "remove animations"
 * setting is on.
 */
@Composable
fun StatusOrb(
    tone: StatusTone,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.OrbMd,
    animated: Boolean = true,
) {
    val color by animateColorAsState(targetValue = tone.color(), label = "orbColor")
    val animation = if (!animated || LocalReduceMotion.current) OrbAnimation.NONE else tone.animation
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse",
    )
    val shimmer by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SHIMMER_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shimmer",
    )

    // The canvas is twice the dot size so the pulse ring has room to expand.
    Canvas(modifier = modifier.size(size * 2)) {
        val core = this.size.minDimension / 4f
        when (animation) {
            OrbAnimation.PULSE -> drawCircle(
                color = color.copy(alpha = 0.45f * (1f - pulse)),
                radius = core * (1f + pulse),
            )
            OrbAnimation.SHIMMER -> drawCircle(
                color = color.copy(alpha = 0.35f * shimmer),
                radius = core * 1.8f,
            )
            OrbAnimation.NONE -> drawCircle(
                color = color.copy(alpha = 0.18f),
                radius = core * 1.6f,
            )
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = if (animation == OrbAnimation.SHIMMER) shimmer else 1f), color),
                center = center,
                radius = core,
            ),
            radius = core,
        )
    }
}

private const val PULSE_MS = 1_600
private const val SHIMMER_MS = 900

@Preview
@Composable
private fun StatusOrbPreview() {
    AppTheme(darkTheme = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusTone.entries.forEach { tone ->
                StatusOrb(tone = tone, size = Dimens.OrbLg)
                Spacer(Modifier.width(Dimens.SpaceSm))
            }
        }
    }
}
