package com.claude.codex.ai.monitoring.domain.models

sealed interface ConnectionStatus {
    /** First attempt, or the socket is open and the handshake has not finished. */
    data object Connecting : ConnectionStatus

    data class Connected(val sinceMs: Long) : ConnectionStatus

    /** A connection dropped; the next attempt is scheduled. */
    data class Reconnecting(val attempt: Int) : ConnectionStatus

    /** Several attempts failed in a row: the computer is probably asleep or the agent is stopped. */
    data class Offline(val lastConnectedAtMs: Long?) : ConnectionStatus

    /** The computer refused this phone, or could not prove its identity. No retries until re-paired. */
    data class Unauthorized(val reason: AuthProblem) : ConnectionStatus
}
