package com.claude.codex.ai.monitoring.presentation.pair.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.LocalReduceMotion

/** Viewfinder frame: rounded gradient corner brackets and a soft scan line sweeping the window. */
@Composable
fun ScannerOverlay(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val transition = rememberInfiniteTransition(label = "scan")
    val sweep by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(SWEEP_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sweep",
    )
    val reduceMotion = LocalReduceMotion.current

    Canvas(modifier = modifier) {
        val side = size.minDimension
        val corner = side * 0.16f
        val stroke = side * 0.018f
        val radius = side * 0.07f
        val brush = colors.brandGradient
        val path = Path().apply {
            // Top-left
            moveTo(0f, corner); lineTo(0f, radius); quadraticTo(0f, 0f, radius, 0f); lineTo(corner, 0f)
            // Top-right
            moveTo(side - corner, 0f); lineTo(side - radius, 0f); quadraticTo(side, 0f, side, radius); lineTo(side, corner)
            // Bottom-right
            moveTo(side, side - corner); lineTo(side, side - radius); quadraticTo(side, side, side - radius, side); lineTo(side - corner, side)
            // Bottom-left
            moveTo(corner, side); lineTo(radius, side); quadraticTo(0f, side, 0f, side - radius); lineTo(0f, side - corner)
        }
        drawPath(path, brush, style = Stroke(width = stroke, cap = StrokeCap.Round))
        if (!reduceMotion) {
            val y = side * sweep
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(colors.brandEnd.copy(alpha = 0f), colors.brandEnd.copy(alpha = 0.85f), colors.brandEnd.copy(alpha = 0f)),
                ),
                topLeft = Offset(side * 0.06f, y - stroke / 2),
                size = Size(side * 0.88f, stroke),
                cornerRadius = CornerRadius(stroke),
            )
        }
    }
}

private const val SWEEP_MS = 1_700

@Preview
@Composable
private fun ScannerOverlayPreview() {
    AppTheme(darkTheme = true) {
        ScannerOverlay(modifier = Modifier.fillMaxWidth().aspectRatio(1f))
    }
}
