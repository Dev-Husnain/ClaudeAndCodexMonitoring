package com.claude.codex.ai.monitoring.domain.models

data class TimelineEventModel(
    val eventId: String,
    val sessionId: String,
    val timestampMs: Long,
    val kind: TimelineEventKind,
    val title: String,
    val detail: String?,
)
