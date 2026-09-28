package com.claude.codex.ai.monitoring.presentation.onboarding.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.theme.LocalReduceMotion
import kotlin.math.cos
import kotlin.math.sin

/**
 * The app mark, alive: a gradient ring with a breathing core and three status satellites
 * (running, waiting, done) slowly orbiting it. Static when reduce-motion is on.
 */
@Composable
fun OnboardingHero(
    modifier: Modifier = Modifier,
    size: Dp = Dimens.SpaceHuge * 4,
) {
    val colors = AppTheme.colors
    val transition = rememberInfiniteTransition(label = "hero")
    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(ORBIT_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "orbit",
    )
    val breath by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BREATH_MS), RepeatMode.Reverse),
        label = "breath",
    )
    val reduceMotion = LocalReduceMotion.current
    val angle = if (reduceMotion) 30f else orbit
    val scale = if (reduceMotion) 1f else breath

    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val ring = radius * 0.55f
        drawCircle(
            brush = Brush.radialGradient(listOf(colors.brandStart.copy(alpha = 0.35f), colors.brandStart.copy(alpha = 0f)), radius = radius),
            radius = radius,
        )
        drawCircle(brush = colors.brandGradient, radius = ring, style = Stroke(width = radius * 0.1f))
        drawCircle(color = colors.running.copy(alpha = 0.25f), radius = ring * 0.62f * scale)
        drawCircle(color = colors.running, radius = ring * 0.36f * scale)
        listOf(colors.running, colors.waiting, colors.done).forEachIndexed { index, color ->
            val theta = Math.toRadians((angle + index * 120f).toDouble())
            val position = Offset(center.x + (radius * 0.86f * cos(theta)).toFloat(), center.y + (radius * 0.86f * sin(theta)).toFloat())
            drawCircle(color = color.copy(alpha = 0.3f), radius = radius * 0.09f, center = position)
            drawCircle(color = color, radius = radius * 0.05f, center = position)
        }
    }
}

private const val ORBIT_MS = 14_000
private const val BREATH_MS = 1_800

@Preview
@Composable
private fun OnboardingHeroPreview() {
    AppTheme(darkTheme = true) {
        OnboardingHero()
    }
}
