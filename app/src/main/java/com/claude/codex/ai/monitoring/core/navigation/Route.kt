package com.claude.codex.ai.monitoring.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Home : Route

    @Serializable
    data class SessionDetail(val sessionId: String) : Route

    @Serializable
    data object Settings : Route
}
