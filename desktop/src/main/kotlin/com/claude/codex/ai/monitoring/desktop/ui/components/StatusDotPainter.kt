package com.claude.codex.ai.monitoring.desktop.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter

/** Tray icon: a ring in the brand colour with a centre dot in the aggregate status colour. */
class StatusDotPainter(
    private val ring: Color,
    private val dot: Color,
) : Painter() {
    override val intrinsicSize: Size = Size(ICON_PX, ICON_PX)

    override fun DrawScope.onDraw() {
        val radius = size.minDimension / 2f
        drawCircle(color = ring, radius = radius * 0.78f, style = Stroke(width = radius * 0.28f))
        drawCircle(color = dot, radius = radius * 0.38f)
    }

    private companion object {
        const val ICON_PX = 32f
    }
}
