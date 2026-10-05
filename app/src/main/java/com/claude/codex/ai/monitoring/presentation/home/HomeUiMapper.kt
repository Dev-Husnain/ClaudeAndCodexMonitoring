package com.claude.codex.ai.monitoring.presentation.home

import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.utils.UiText
import com.claude.codex.ai.monitoring.core.utils.stripMarkdown
import com.claude.codex.ai.monitoring.core.utils.toRelativeTime
import com.claude.codex.ai.monitoring.domain.models.AuthProblem
import com.claude.codex.ai.monitoring.domain.models.ConnectionStatus
import com.claude.codex.ai.monitoring.domain.models.SessionControl
import com.claude.codex.ai.monitoring.domain.models.SessionModel
import com.claude.codex.ai.monitoring.domain.models.SessionOverviewModel
import com.claude.codex.ai.monitoring.domain.models.SessionStatus
import com.claude.codex.ai.monitoring.presentation.common.toLabel
import com.claude.codex.ai.monitoring.presentation.common.toTone
import com.claude.codex.ai.monitoring.presentation.common.toUiModel

/** [pairedComputerName] comes from the pairing, so it is known even before the first `ready`. */
fun SessionOverviewModel.toHomeUiState(nowMs: Long, pairedComputerName: String?): HomeUiState {
    val offline = connection is ConnectionStatus.Offline
    val computerName = computer?.name ?: pairedComputerName
    val computerText = computerName?.let { UiText.Raw(it) } ?: UiText.Res(R.string.home_your_computer)
    val unauthorized = (connection as? ConnectionStatus.Unauthorized)?.reason?.let { reason ->
        UnauthorizedUiModel(
            title = when (reason) {
                AuthProblem.REVOKED -> R.string.home_unauthorized_revoked_title
                AuthProblem.NOT_PAIRED -> R.string.home_unauthorized_not_paired_title
                AuthProblem.AUTH_FAILED -> R.string.home_unauthorized_auth_title
                AuthProblem.DESKTOP_MISMATCH -> R.string.home_unauthorized_mismatch_title
            },
            message = UiText.Res(
                when (reason) {
                    AuthProblem.REVOKED -> R.string.home_unauthorized_revoked_message
                    AuthProblem.NOT_PAIRED -> R.string.home_unauthorized_not_paired_message
                    AuthProblem.AUTH_FAILED -> R.string.home_unauthorized_auth_message
                    AuthProblem.DESKTOP_MISMATCH -> R.string.home_unauthorized_mismatch_message
                },
                listOf(computerText),
            ),
        )
    }
    val sessionCount = needsYou.size + projects.sumOf { it.sessions.size }
    return HomeUiState(
        connection = connection.toUiModel(),
        computerName = computerName,
        readOnly = hasSnapshot && !canSendInput && unauthorized == null,
        canControl = hasSnapshot && canSendInput && unauthorized == null,
        awayMode = awayMode,
        isLoading = !hasSnapshot && !offline && unauthorized == null,
        isOffline = !hasSnapshot && offline,
        offlineMessage = UiText.Res(R.string.home_offline_message, listOf(computerText)),
        unauthorized = unauthorized,
        isEmpty = hasSnapshot && sessionCount == 0 && projects.isEmpty() && unauthorized == null,
        staleNotice = lastConnectedAtMs
            ?.takeIf { hasSnapshot && offline }
            ?.let { UiText.Res(R.string.home_offline_last_seen, listOf(it.toRelativeTime(nowMs))) },
        needsYou = needsYou.map { it.toItemUiModel(projectNames, nowMs) },
        projects = projects.map { group ->
            ProjectSectionUiModel(
                projectId = group.project.projectId,
                name = group.project.name,
                sessions = group.sessions.map { it.toItemUiModel(projectNames, nowMs) },
                lastActivityMs = group.lastActivityMs,
            )
        },
    )
}

private fun SessionModel.toItemUiModel(projectNames: Map<String, String>, nowMs: Long) = SessionItemUiModel(
    sessionId = sessionId,
    projectId = projectId,
    claudeSessionId = claudeSessionId,
    projectName = projectNames[projectId],
    topic = title,
    tone = status.toTone(),
    statusLabel = status.toLabel(),
    snippet = errorInfo ?: lastMessageSnippet?.stripMarkdown()?.ifBlank { null },
    lastTool = lastTool?.let { UiText.Res(R.string.home_last_tool, listOf(it)) },
    relativeTime = lastEventAtMs.toRelativeTime(nowMs),
    isMonitorOnly = control == SessionControl.MONITOR_ONLY,
    removable = status in REMOVABLE && awaiting == null,
    lastActivityMs = lastEventAtMs,
)

private val REMOVABLE = setOf(SessionStatus.ENDED, SessionStatus.IDLE, SessionStatus.ERROR, SessionStatus.STALE)
