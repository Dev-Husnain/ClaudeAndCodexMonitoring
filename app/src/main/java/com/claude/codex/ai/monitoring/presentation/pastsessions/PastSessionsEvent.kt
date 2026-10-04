package com.claude.codex.ai.monitoring.presentation.pastsessions

sealed interface PastSessionsEvent {
    data object OnRetryClick : PastSessionsEvent

    data class OnSessionClick(val claudeSessionId: String) : PastSessionsEvent

    data class OnSessionLongClick(val claudeSessionId: String) : PastSessionsEvent

    data class OnResumeTextChange(val text: String) : PastSessionsEvent

    data object OnResumeConfirm : PastSessionsEvent

    data object OnResumeDismiss : PastSessionsEvent

    data object OnRemoveConfirm : PastSessionsEvent

    data object OnRemoveDismiss : PastSessionsEvent
}
