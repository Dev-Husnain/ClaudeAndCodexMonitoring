package com.claude.codex.ai.monitoring.presentation.sessiondetail

import com.claude.codex.ai.monitoring.domain.models.QuickActionType
import com.claude.codex.ai.monitoring.domain.models.TerminalKeyType

sealed interface SessionDetailEvent {
    data object OnRetryClick : SessionDetailEvent

    data class OnComposerChange(val text: String) : SessionDetailEvent

    data object OnSendClick : SessionDetailEvent

    data class OnQuickAction(val action: QuickActionType) : SessionDetailEvent

    data class OnAwayModeToggle(val enabled: Boolean) : SessionDetailEvent

    data class OnTabSelect(val tab: DetailTab) : SessionDetailEvent

    data class OnTerminalKey(val key: TerminalKeyType) : SessionDetailEvent
}
