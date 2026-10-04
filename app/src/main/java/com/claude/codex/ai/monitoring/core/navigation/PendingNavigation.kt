package com.claude.codex.ai.monitoring.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A session to open that came from outside the UI (a tapped alert), until the nav graph handles it. */
class PendingNavigation {
    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()

    fun openSession(sessionId: String) {
        _sessionId.value = sessionId
    }

    fun consume() {
        _sessionId.value = null
    }
}
