package com.claude.codex.ai.monitoring.presentation.sessiondetail

import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.utils.UiText
import com.claude.codex.ai.monitoring.core.utils.toRelativeTime
import com.claude.codex.ai.monitoring.domain.models.AwaitingKind
import com.claude.codex.ai.monitoring.domain.models.ConnectionStatus
import com.claude.codex.ai.monitoring.domain.models.DeliveryStatus
import com.claude.codex.ai.monitoring.domain.models.SessionStatus
import com.claude.codex.ai.monitoring.domain.models.SessionControl
import com.claude.codex.ai.monitoring.domain.models.SessionDetailModel
import com.claude.codex.ai.monitoring.domain.models.SessionModel
import com.claude.codex.ai.monitoring.domain.models.TimelineEventKind
import com.claude.codex.ai.monitoring.domain.models.TimelineEventModel
import com.claude.codex.ai.monitoring.presentation.common.toLabel
import com.claude.codex.ai.monitoring.presentation.common.toTone
import com.claude.codex.ai.monitoring.presentation.common.toUiModel

private const val SHORT_ID_LENGTH = 8

fun SessionDetailModel.toUiState(sessionId: String, nowMs: Long): SessionDetailUiState {
    val offline = connection is ConnectionStatus.Offline
    return SessionDetailUiState(
        title = projectName ?: session?.projectId.orEmpty(),
        subtitle = sessionId.take(SHORT_ID_LENGTH),
        connection = connection.toUiModel(),
        isLoading = !hasSnapshot && !offline,
        isNotFound = (hasSnapshot && session == null) || (!hasSnapshot && offline),
        isOffline = offline,
        header = session?.toHeader(nowMs),
        timeline = timeline?.asReversed()?.map { it.toItem(nowMs) },
        canControl = canSendInput,
        showReadOnlyNote = hasSnapshot && session != null && !canSendInput,
        showComposer = canSendInput && session != null && session.status != SessionStatus.ENDED,
        awayMode = awayMode,
        awaiting = session?.awaiting?.let { AwaitingUiModel(isPermission = it.kind == AwaitingKind.PERMISSION, detail = it.detail) },
    )
}

/** The note under the composer, and its colour, for a delivery result. */
fun DeliveryStatus.toNote(): Pair<UiText, StatusTone> = when (this) {
    DeliveryStatus.Delivered -> UiText.Res(R.string.delivery_delivered) to StatusTone.DONE
    DeliveryStatus.Queued -> UiText.Res(R.string.delivery_queued) to StatusTone.WAITING
    is DeliveryStatus.Failed -> UiText.Res(
        when (reason) {
            DeliveryStatus.FailureReason.NOT_CONNECTED -> R.string.delivery_not_connected
            DeliveryStatus.FailureReason.READ_ONLY -> R.string.delivery_read_only
            DeliveryStatus.FailureReason.NOT_WAITING -> R.string.delivery_not_waiting
            DeliveryStatus.FailureReason.SESSION_GONE -> R.string.delivery_gone
            DeliveryStatus.FailureReason.TIMEOUT -> R.string.delivery_timeout
            DeliveryStatus.FailureReason.REJECTED -> R.string.delivery_rejected
        },
    ) to StatusTone.ERROR
}

private fun SessionModel.toHeader(nowMs: Long) = SessionHeaderUiModel(
    tone = status.toTone(),
    statusLabel = status.toLabel(),
    started = UiText.Res(R.string.detail_started, listOf(startedAtMs.toRelativeTime(nowMs))),
    lastTool = lastTool,
    controlLabel = UiText.Res(
        when (control) {
            SessionControl.MONITOR_ONLY -> R.string.detail_control_monitor_only
            SessionControl.WRAPPER -> R.string.detail_control_wrapper
            SessionControl.HEADLESS -> R.string.detail_control_headless
            SessionControl.HOOKS -> R.string.detail_control_hooks
        },
    ),
    controlTone = if (control == SessionControl.MONITOR_ONLY) StatusTone.STALE else StatusTone.BRAND,
    message = lastMessageSnippet,
    errorInfo = errorInfo,
)

private fun TimelineEventModel.toItem(nowMs: Long) = TimelineItemUiModel(
    eventId = eventId,
    icon = when (kind) {
        TimelineEventKind.SESSION_START -> R.drawable.ic_play
        TimelineEventKind.PROMPT -> R.drawable.ic_user
        TimelineEventKind.TOOL_USE, TimelineEventKind.TOOL_RESULT -> R.drawable.ic_tool
        TimelineEventKind.NOTIFICATION -> R.drawable.ic_bell
        TimelineEventKind.MESSAGE -> R.drawable.ic_message
        TimelineEventKind.STOP -> R.drawable.ic_check
        TimelineEventKind.ERROR -> R.drawable.ic_alert
        TimelineEventKind.SESSION_END -> R.drawable.ic_flag
    },
    tone = when (kind) {
        TimelineEventKind.NOTIFICATION -> StatusTone.WAITING
        TimelineEventKind.ERROR -> StatusTone.ERROR
        TimelineEventKind.STOP, TimelineEventKind.SESSION_END -> StatusTone.DONE
        TimelineEventKind.TOOL_USE, TimelineEventKind.TOOL_RESULT -> StatusTone.RUNNING
        TimelineEventKind.SESSION_START, TimelineEventKind.PROMPT, TimelineEventKind.MESSAGE -> StatusTone.BRAND
    },
    title = title,
    detail = detail,
    time = timestampMs.toRelativeTime(nowMs),
)
