package com.claude.codex.ai.monitoring.presentation.devicessecurity

sealed interface DevicesSecurityEvent {
    data object OnUnpairClick : DevicesSecurityEvent

    data object OnUnpairConfirm : DevicesSecurityEvent

    data object OnUnpairDismiss : DevicesSecurityEvent
}
