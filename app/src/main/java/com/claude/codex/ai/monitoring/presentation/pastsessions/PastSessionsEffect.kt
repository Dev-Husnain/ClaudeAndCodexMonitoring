package com.claude.codex.ai.monitoring.presentation.pastsessions

sealed interface PastSessionsEffect {
    /** Show the session (a resumed one, or one Claude still has open). */
    data class OpenSession(val sessionId: String) : PastSessionsEffect
}
