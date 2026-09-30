package com.claude.codex.ai.monitoring.domain.models

/** What happened to input sent from the phone, as confirmed by the computer. */
sealed interface DeliveryStatus {
    data object Delivered : DeliveryStatus

    /** Accepted; Claude gets it when it finishes its current turn. */
    data object Queued : DeliveryStatus

    data class Failed(val reason: FailureReason, val detail: String? = null) : DeliveryStatus

    enum class FailureReason { NOT_CONNECTED, READ_ONLY, NOT_WAITING, SESSION_GONE, TIMEOUT, REJECTED }
}
