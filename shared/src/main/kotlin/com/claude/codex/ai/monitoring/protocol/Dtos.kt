package com.claude.codex.ai.monitoring.protocol

import kotlinx.serialization.Serializable

@Serializable
data class ComputerDto(
    val computerId: String,
    val name: String,
)

@Serializable
data class ProjectDto(
    val projectId: String,
    val name: String,
)

@Serializable
data class SessionDto(
    val sessionId: String,
    val projectId: String,
    val state: SessionState,
    val controlMode: ControlMode,
    val startedAt: Long,
    val lastEventAt: Long,
    val lastMessageSnippet: String? = null,
    val lastTool: String? = null,
    val errorInfo: String? = null,
    /** Set while Away mode holds Claude for the owner's answer. */
    val awaiting: AwaitingDto? = null,
)

@Serializable
data class AwaitingDto(
    val kind: AwaitingKind,
    /** For PERMISSION: what Claude wants to do, e.g. "Bash: ./gradlew test". For REPLY: Claude's last message. */
    val detail: String? = null,
    val sinceMs: Long,
)

@Serializable
data class TimelineEventDto(
    val eventId: String,
    val sessionId: String,
    val ts: Long,
    val kind: EventKind,
    val title: String,
    val detail: String? = null,
)
