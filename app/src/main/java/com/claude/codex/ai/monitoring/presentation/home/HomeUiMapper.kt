package com.claude.codex.ai.monitoring.presentation.home

import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.utils.UiText
import com.claude.codex.ai.monitoring.core.utils.toRelativeTime
import com.claude.codex.ai.monitoring.domain.models.ConnectionStatus
import com.claude.codex.ai.monitoring.domain.models.SessionControl
import com.claude.codex.ai.monitoring.domain.models.SessionModel
import com.claude.codex.ai.monitoring.domain.models.SessionOverviewModel
import com.claude.codex.ai.monitoring.presentation.common.toLabel
import com.claude.codex.ai.monitoring.presentation.common.toTone
import com.claude.codex.ai.monitoring.presentation.common.toUiModel

fun SessionOverviewModel.toHomeUiState(nowMs: Long): HomeUiState {
    val offline = connection is ConnectionStatus.Offline
    val sessionCount = needsYou.size + projects.sumOf { it.sessions.size }
    return HomeUiState(
        connection = connection.toUiModel(),
        computerName = computer?.name,
        isLoading = !hasSnapshot && !offline,
        isOffline = !hasSnapshot && offline,
        isEmpty = hasSnapshot && sessionCount == 0,
        staleNotice = lastConnectedAtMs
            ?.takeIf { hasSnapshot && offline }
            ?.let { UiText.Res(R.string.home_offline_last_seen, listOf(it.toRelativeTime(nowMs))) },
        needsYou = needsYou.map { it.toItemUiModel(projectNames, nowMs) },
        projects = projects.map { group ->
            ProjectSectionUiModel(
                projectId = group.project.projectId,
                name = group.project.name,
                sessions = group.sessions.map { it.toItemUiModel(projectNames, nowMs) },
            )
        },
    )
}

private fun SessionModel.toItemUiModel(projectNames: Map<String, String>, nowMs: Long) = SessionItemUiModel(
    sessionId = sessionId,
    projectName = projectNames[projectId] ?: projectId,
    tone = status.toTone(),
    statusLabel = status.toLabel(),
    snippet = errorInfo ?: lastMessageSnippet,
    lastTool = lastTool?.let { UiText.Res(R.string.home_last_tool, listOf(it)) },
    relativeTime = lastEventAtMs.toRelativeTime(nowMs),
    isMonitorOnly = control == SessionControl.MONITOR_ONLY,
)
