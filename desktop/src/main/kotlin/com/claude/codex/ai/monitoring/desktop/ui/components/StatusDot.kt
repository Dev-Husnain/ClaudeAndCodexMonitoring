package com.claude.codex.ai.monitoring.desktop.ui.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Status dot with a soft pulse ring when [pulsing]. */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
    pulsing: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "dot")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse",
    )
    Canvas(modifier = modifier.size(size * 2)) {
        val core = this.size.minDimension / 4f
        if (pulsing) {
            drawCircle(color = color.copy(alpha = 0.45f * (1f - pulse)), radius = core * (1f + pulse))
        } else {
            drawCircle(color = color.copy(alpha = 0.18f), radius = core * 1.6f)
        }
        drawCircle(color = color, radius = core)
    }
}
