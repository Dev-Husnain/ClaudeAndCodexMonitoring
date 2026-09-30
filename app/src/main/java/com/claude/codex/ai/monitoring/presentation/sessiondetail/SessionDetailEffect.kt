package com.claude.codex.ai.monitoring.presentation.sessiondetail

sealed interface SessionDetailEffect {
    /** A short vibration after sending or answering (only when haptics are on in Settings). */
    data class Haptic(val success: Boolean) : SessionDetailEffect
}
