package com.claude.codex.ai.monitoring.domain.models

data class SessionDetailModel(
    val connection: ConnectionStatus,
    val hasSnapshot: Boolean,
    /** Null when the computer does not know this session (any more). */
    val session: SessionModel?,
    val projectName: String?,
    /** Null while history has not been loaded yet. */
    val timeline: List<TimelineEventModel>?,
)
