package com.claude.codex.ai.monitoring.presentation.home

sealed interface HomeEvent {
    data object OnRetryClick : HomeEvent
}
