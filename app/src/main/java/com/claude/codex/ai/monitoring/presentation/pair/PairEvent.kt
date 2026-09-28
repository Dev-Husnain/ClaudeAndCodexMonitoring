package com.claude.codex.ai.monitoring.presentation.pair

sealed interface PairEvent {
    data class OnCodeScanned(val raw: String) : PairEvent

    data object OnToggleCodeEntry : PairEvent

    data class OnCodeInputChange(val value: String) : PairEvent

    data object OnCodeSubmit : PairEvent

    data class OnDeviceNameChange(val value: String) : PairEvent

    data object OnSendRequest : PairEvent

    data object OnRetry : PairEvent
}
