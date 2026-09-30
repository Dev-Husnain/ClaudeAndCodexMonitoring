package com.claude.codex.ai.monitoring.data.mapper

import com.claude.codex.ai.monitoring.domain.models.AwaitingKind
import com.claude.codex.ai.monitoring.domain.models.AwaitingModel
import com.claude.codex.ai.monitoring.domain.models.ComputerModel
import com.claude.codex.ai.monitoring.domain.models.QuickActionType
import com.claude.codex.ai.monitoring.domain.models.ProjectModel
import com.claude.codex.ai.monitoring.domain.models.SessionControl
import com.claude.codex.ai.monitoring.domain.models.SessionModel
import com.claude.codex.ai.monitoring.domain.models.SessionStatus
import com.claude.codex.ai.monitoring.domain.models.TimelineEventKind
import com.claude.codex.ai.monitoring.domain.models.TimelineEventModel
import com.claude.codex.ai.monitoring.protocol.AwaitingDto
import com.claude.codex.ai.monitoring.protocol.ComputerDto
import com.claude.codex.ai.monitoring.protocol.QuickAction
import com.claude.codex.ai.monitoring.protocol.ControlMode
import com.claude.codex.ai.monitoring.protocol.EventKind
import com.claude.codex.ai.monitoring.protocol.ProjectDto
import com.claude.codex.ai.monitoring.protocol.SessionDto
import com.claude.codex.ai.monitoring.protocol.SessionState
import com.claude.codex.ai.monitoring.protocol.TimelineEventDto

fun ComputerDto.toModel() = ComputerModel(computerId = computerId, name = name)

fun ProjectDto.toModel() = ProjectModel(projectId = projectId, name = name)

fun SessionDto.toModel() = SessionModel(
    sessionId = sessionId,
    projectId = projectId,
    status = state.toModel(),
    control = controlMode.toModel(),
    startedAtMs = startedAt,
    lastEventAtMs = lastEventAt,
    lastMessageSnippet = lastMessageSnippet,
    lastTool = lastTool,
    errorInfo = errorInfo,
    awaiting = awaiting?.toModel(),
)

fun TimelineEventDto.toModel() = TimelineEventModel(
    eventId = eventId,
    sessionId = sessionId,
    timestampMs = ts,
    kind = kind.toModel(),
    title = title,
    detail = detail,
)

fun SessionState.toModel(): SessionStatus = when (this) {
    SessionState.RUNNING -> SessionStatus.RUNNING
    SessionState.WAITING_INPUT -> SessionStatus.WAITING_INPUT
    SessionState.IDLE -> SessionStatus.IDLE
    SessionState.ERROR -> SessionStatus.ERROR
    SessionState.ENDED -> SessionStatus.ENDED
    SessionState.STALE -> SessionStatus.STALE
}

fun ControlMode.toModel(): SessionControl = when (this) {
    ControlMode.MONITOR_ONLY -> SessionControl.MONITOR_ONLY
    ControlMode.WRAPPER -> SessionControl.WRAPPER
    ControlMode.HEADLESS -> SessionControl.HEADLESS
    ControlMode.HOOKS -> SessionControl.HOOKS
}

fun AwaitingDto.toModel() = AwaitingModel(
    kind = when (kind) {
        com.claude.codex.ai.monitoring.protocol.AwaitingKind.PERMISSION -> AwaitingKind.PERMISSION
        com.claude.codex.ai.monitoring.protocol.AwaitingKind.REPLY -> AwaitingKind.REPLY
    },
    detail = detail,
    sinceMs = sinceMs,
)

fun QuickActionType.toDto(): QuickAction = when (this) {
    QuickActionType.APPROVE -> QuickAction.APPROVE
    QuickActionType.DENY -> QuickAction.DENY
    QuickActionType.INTERRUPT -> QuickAction.INTERRUPT
    QuickActionType.CONTINUE -> QuickAction.CONTINUE
}

fun EventKind.toModel(): TimelineEventKind = when (this) {
    EventKind.SESSION_START -> TimelineEventKind.SESSION_START
    EventKind.PROMPT -> TimelineEventKind.PROMPT
    EventKind.TOOL_USE -> TimelineEventKind.TOOL_USE
    EventKind.TOOL_RESULT -> TimelineEventKind.TOOL_RESULT
    EventKind.NOTIFICATION -> TimelineEventKind.NOTIFICATION
    EventKind.MESSAGE -> TimelineEventKind.MESSAGE
    EventKind.STOP -> TimelineEventKind.STOP
    EventKind.ERROR -> TimelineEventKind.ERROR
    EventKind.SESSION_END -> TimelineEventKind.SESSION_END
}
