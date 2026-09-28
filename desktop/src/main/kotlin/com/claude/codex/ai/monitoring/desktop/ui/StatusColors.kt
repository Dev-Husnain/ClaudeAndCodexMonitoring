package com.claude.codex.ai.monitoring.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.claude.codex.ai.monitoring.desktop.ui.theme.DesktopColors
import com.claude.codex.ai.monitoring.desktop.ui.theme.DesktopTheme
import com.claude.codex.ai.monitoring.protocol.SessionDto
import com.claude.codex.ai.monitoring.protocol.SessionState

fun SessionState.color(colors: DesktopColors): Color = when (this) {
    SessionState.RUNNING -> colors.running
    SessionState.WAITING_INPUT -> colors.waiting
    SessionState.IDLE, SessionState.ENDED -> colors.done
    SessionState.ERROR -> colors.error
    SessionState.STALE -> colors.stale
}

@Composable
fun SessionState.color(): Color = color(DesktopTheme.colors)

fun SessionState.label(): String = when (this) {
    SessionState.RUNNING -> "Running"
    SessionState.WAITING_INPUT -> "Needs you"
    SessionState.IDLE -> "Idle"
    SessionState.ERROR -> "Error"
    SessionState.ENDED -> "Ended"
    SessionState.STALE -> "Possibly stuck"
}

/** The most urgent state across sessions, for the tray dot: waiting > error > running > idle. */
fun List<SessionDto>.aggregateState(): SessionState? = when {
    isEmpty() -> null
    any { it.state == SessionState.WAITING_INPUT } -> SessionState.WAITING_INPUT
    any { it.state == SessionState.ERROR } -> SessionState.ERROR
    any { it.state == SessionState.RUNNING } -> SessionState.RUNNING
    any { it.state == SessionState.STALE } -> SessionState.STALE
    else -> SessionState.IDLE
}
