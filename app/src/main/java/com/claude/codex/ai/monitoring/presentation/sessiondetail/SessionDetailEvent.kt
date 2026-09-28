package com.claude.codex.ai.monitoring.presentation.sessiondetail

sealed interface SessionDetailEvent {
    data object OnRetryClick : SessionDetailEvent
}
