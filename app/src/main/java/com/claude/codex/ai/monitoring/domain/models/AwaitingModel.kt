package com.claude.codex.ai.monitoring.domain.models

enum class AwaitingKind { PERMISSION, REPLY }

/** What a session is waiting for from the owner while Away mode holds it. */
data class AwaitingModel(
    val kind: AwaitingKind,
    val detail: String?,
    val sinceMs: Long,
)
