package com.claude.codex.ai.monitoring.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.claude.codex.ai.monitoring.desktop.ui.theme.DesktopTheme

/** Ring that drains as the pairing code expires, with the remaining time in the middle. */
@Composable
fun CountdownRing(
    remainingMs: Long,
    totalMs: Long,
    modifier: Modifier = Modifier,
) {
    val colors = DesktopTheme.colors
    val fraction = (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f)
    val ringColor = if (fraction < 0.25f) colors.waiting else colors.brandEnd
    val seconds = (remainingMs / 1000).coerceAtLeast(0)
    Box(modifier = modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(64.dp)) {
            val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            drawArc(colors.outline, 0f, 360f, useCenter = false, style = stroke)
            drawArc(ringColor, -90f, 360f * fraction, useCenter = false, style = stroke)
        }
        Text("%d:%02d".format(seconds / 60, seconds % 60), style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
    }
}
