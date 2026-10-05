package com.claude.codex.ai.monitoring.data.mapper

import com.claude.codex.ai.monitoring.domain.models.AwaitingKind
import com.claude.codex.ai.monitoring.domain.models.AwaitingModel
import com.claude.codex.ai.monitoring.domain.models.ComputerModel
import com.claude.codex.ai.monitoring.domain.models.PastSessionModel
import com.claude.codex.ai.monitoring.domain.models.QuickActionType
import com.claude.codex.ai.monitoring.domain.models.ProjectModel
import com.claude.codex.ai.monitoring.domain.models.SessionControl
import com.claude.codex.ai.monitoring.domain.models.SessionModel
import com.claude.codex.ai.monitoring.domain.models.SessionStatus
import com.claude.codex.ai.monitoring.domain.models.TimelineEventKind
import com.claude.codex.ai.monitoring.domain.models.TimelineEventModel
import com.claude.codex.ai.monitoring.domain.models.TerminalColor
import com.claude.codex.ai.monitoring.domain.models.TerminalKeyType
import com.claude.codex.ai.monitoring.domain.models.TerminalLineModel
import com.claude.codex.ai.monitoring.domain.models.TerminalScreenModel
import com.claude.codex.ai.monitoring.domain.models.TerminalSpanModel
import com.claude.codex.ai.monitoring.protocol.AwaitingDto
import com.claude.codex.ai.monitoring.protocol.ComputerDto
import com.claude.codex.ai.monitoring.protocol.QuickAction
import com.claude.codex.ai.monitoring.protocol.ControlMode
import com.claude.codex.ai.monitoring.protocol.EventKind
import com.claude.codex.ai.monitoring.protocol.ProjectDto
import com.claude.codex.ai.monitoring.protocol.SessionDto
import com.claude.codex.ai.monitoring.protocol.SessionState
import com.claude.codex.ai.monitoring.protocol.TimelineEventDto
import com.claude.codex.ai.monitoring.protocol.Message
import com.claude.codex.ai.monitoring.protocol.PastSessionDto
import com.claude.codex.ai.monitoring.protocol.TerminalKey
import com.claude.codex.ai.monitoring.protocol.TerminalSpanDto

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
    claudeSessionId = claudeSessionId,
    title = title,
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

fun Message.TerminalScreen.toModel() = TerminalScreenModel(
    columns = columns,
    lines = lines.map { line -> TerminalLineModel(line.spans.map { it.toModel() }) },
)

private fun TerminalSpanDto.toModel() = TerminalSpanModel(
    text = text,
    foreground = fg.toTerminalColor(),
    background = bg.toTerminalColor(),
    bold = bold,
    italic = italic,
    underline = underline,
    dim = dim,
    inverse = inverse,
)

private fun Int?.toTerminalColor(): TerminalColor? = when {
    this == null -> null
    this and TerminalSpanDto.RGB_FLAG != 0 -> TerminalColor.Rgb(this and RGB_MASK)
    this in 0..PALETTE_MAX -> TerminalColor.Palette(this)
    else -> null
}

private const val RGB_MASK = 0xFFFFFF
private const val PALETTE_MAX = 255

fun PastSessionDto.toModel() = PastSessionModel(claudeSessionId = claudeSessionId, title = title, lastActiveAtMs = lastActiveAt)

fun TerminalKeyType.toDto(): TerminalKey = when (this) {
    TerminalKeyType.ENTER -> TerminalKey.ENTER
    TerminalKeyType.ESCAPE -> TerminalKey.ESCAPE
    TerminalKeyType.TAB -> TerminalKey.TAB
    TerminalKeyType.SHIFT_TAB -> TerminalKey.SHIFT_TAB
    TerminalKeyType.UP -> TerminalKey.UP
    TerminalKeyType.DOWN -> TerminalKey.DOWN
    TerminalKeyType.CTRL_C -> TerminalKey.CTRL_C
    TerminalKeyType.DIGIT_1 -> TerminalKey.DIGIT_1
    TerminalKeyType.DIGIT_2 -> TerminalKey.DIGIT_2
    TerminalKeyType.DIGIT_3 -> TerminalKey.DIGIT_3
}

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
