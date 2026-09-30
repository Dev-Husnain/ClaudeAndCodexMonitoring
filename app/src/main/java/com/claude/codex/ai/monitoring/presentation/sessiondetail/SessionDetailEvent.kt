package com.claude.codex.ai.monitoring.presentation.sessiondetail

import com.claude.codex.ai.monitoring.domain.models.QuickActionType

sealed interface SessionDetailEvent {
    data object OnRetryClick : SessionDetailEvent

    data class OnComposerChange(val text: String) : SessionDetailEvent

    data object OnSendClick : SessionDetailEvent

    data class OnQuickAction(val action: QuickActionType) : SessionDetailEvent

    data class OnAwayModeToggle(val enabled: Boolean) : SessionDetailEvent
}
