package com.claude.codex.ai.monitoring.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Onboarding : Route

    @Serializable
    data object Pair : Route

    @Serializable
    data object Home : Route

    @Serializable
    data class SessionDetail(val sessionId: String) : Route

    @Serializable
    data class PastSessions(val projectId: String, val projectName: String) : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object DevicesSecurity : Route
}
