package com.claude.codex.ai.monitoring.presentation.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.ui.InfoBanner
import com.claude.codex.ai.monitoring.core.ui.SectionHeader
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.utils.UiText
import com.claude.codex.ai.monitoring.core.utils.resolve
import com.claude.codex.ai.monitoring.presentation.home.ProjectSectionUiModel
import com.claude.codex.ai.monitoring.presentation.home.SessionItemUiModel

/** "Needs you" pinned at the top, then every project with its sessions. */
@Composable
fun HomeSessionList(
    needsYou: List<SessionItemUiModel>,
    projects: List<ProjectSectionUiModel>,
    staleNotice: UiText?,
    onSessionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSessionLongClick: (String) -> Unit = {},
    onHistoryClick: (projectId: String, projectName: String) -> Unit = { _, _ -> },
    onRemoveProjectClick: (projectId: String) -> Unit = {},
) {
    val listState = rememberLazyListState()
    // "Needs you" is inserted above what is on screen, where the scroll anchor would hide it.
    // Scroll up whenever a session newly needs the user.
    val needsYouIds = remember(needsYou) { needsYou.mapTo(HashSet()) { it.sessionId } }
    LaunchedEffect(needsYouIds) {
        if (needsYouIds.isNotEmpty()) listState.animateScrollToItem(0)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, bottom = Dimens.SpaceXxl),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        if (staleNotice != null) {
            item(key = "stale", contentType = "banner") {
                InfoBanner(icon = R.drawable.ic_offline, text = staleNotice.resolve(), tone = StatusTone.ERROR)
            }
        }
        if (needsYou.isNotEmpty()) {
            item(key = "needs-you-header", contentType = "header") {
                SectionHeader(
                    title = stringResource(R.string.home_section_needs_you),
                    count = needsYou.size,
                    tone = StatusTone.WAITING,
                    modifier = Modifier.padding(top = Dimens.SpaceSm),
                )
            }
            items(needsYou, key = { "needs-${it.sessionId}" }, contentType = { "session" }) { session ->
                SessionCard(
                    session = session,
                    onClick = { onSessionClick(session.sessionId) },
                    onLongClick = { onSessionLongClick(session.sessionId) },
                    highlighted = true,
                    modifier = Modifier.animateItem(),
                )
            }
        }
        projects.forEach { project ->
            item(key = "project-${project.projectId}", contentType = "header") {
                SectionHeader(
                    title = project.name,
                    count = project.sessions.size.takeIf { it > 0 },
                    actionLabel = stringResource(R.string.home_history),
                    onAction = { onHistoryClick(project.projectId, project.name) },
                    modifier = Modifier.padding(top = Dimens.SpaceMd).animateItem(),
                )
            }
            if (project.sessions.isEmpty()) {
                item(key = "project-empty-${project.projectId}", contentType = "hint") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.animateItem()) {
                        Text(
                            text = stringResource(R.string.home_project_no_sessions),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onRemoveProjectClick(project.projectId) }) {
                            Text(text = stringResource(R.string.remove_project_action), color = AppTheme.colors.textSecondary)
                        }
                    }
                }
            }
            items(project.sessions, key = { it.sessionId }, contentType = { "session" }) { session ->
                SessionCard(
                    session = session,
                    onClick = { onSessionClick(session.sessionId) },
                    onLongClick = { onSessionLongClick(session.sessionId) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Preview
@Composable
private fun HomeSessionListPreview() {
    val session = SessionItemUiModel(
        sessionId = "s1",
        projectName = "ClaudeMonitoring",
        tone = StatusTone.RUNNING,
        statusLabel = UiText.Raw("Running"),
        snippet = "Refactoring the connection repository",
        lastTool = UiText.Raw("Last tool: Edit"),
        relativeTime = UiText.Raw("just now"),
        isMonitorOnly = false,
    )
    AppTheme(darkTheme = true) {
        HomeSessionList(
            needsYou = listOf(session.copy(sessionId = "s2", tone = StatusTone.WAITING, statusLabel = UiText.Raw("Needs you"))),
            projects = listOf(ProjectSectionUiModel("p1", "ClaudeMonitoring", listOf(session))),
            staleNotice = null,
            onSessionClick = {},
        )
    }
}
