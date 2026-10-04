package com.claude.codex.ai.monitoring.domain.models

/** A conversation Claude Code saved on the computer, which the phone can continue. */
data class PastSessionModel(
    val claudeSessionId: String,
    val title: String,
    val lastActiveAtMs: Long,
)

/** The computer's answer to a resume: on success, the session to open. */
data class ResumeOutcomeModel(
    val status: DeliveryStatus,
    val sessionId: String?,
)
