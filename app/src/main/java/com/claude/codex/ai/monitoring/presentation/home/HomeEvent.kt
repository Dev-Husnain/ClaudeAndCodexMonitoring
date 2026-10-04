package com.claude.codex.ai.monitoring.presentation.home

sealed interface HomeEvent {
    data object OnRetryClick : HomeEvent

    /** Forget the refusing computer; the app returns to onboarding to pair again. */
    data object OnPairAgainClick : HomeEvent

    data class OnAwayModeToggle(val enabled: Boolean) : HomeEvent

    /** Long press on a session card. */
    data class OnRemoveRequest(val sessionId: String) : HomeEvent

    data object OnRemoveConfirm : HomeEvent

    data object OnRemoveDismiss : HomeEvent
}
