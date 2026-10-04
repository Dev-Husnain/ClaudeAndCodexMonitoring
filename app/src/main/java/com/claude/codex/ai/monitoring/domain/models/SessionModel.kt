package com.claude.codex.ai.monitoring.domain.models

data class SessionModel(
    val sessionId: String,
    val projectId: String,
    val status: SessionStatus,
    val control: SessionControl,
    val startedAtMs: Long,
    val lastEventAtMs: Long,
    val lastMessageSnippet: String?,
    val lastTool: String?,
    val errorInfo: String?,
    val awaiting: AwaitingModel? = null,
    /** Claude's own id when it differs from [sessionId] (a wrapper terminal's conversation). */
    val claudeSessionId: String? = null,
)
