package com.claude.codex.ai.monitoring.presentation.computerprojects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.theme.MonoTextStyle
import com.claude.codex.ai.monitoring.core.ui.AppTopBar
import com.claude.codex.ai.monitoring.core.ui.AuroraBackground
import com.claude.codex.ai.monitoring.core.ui.QuickActionChip
import com.claude.codex.ai.monitoring.core.ui.StateMessage
import com.claude.codex.ai.monitoring.core.ui.StatusPill
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.ui.SurfaceCard
import com.claude.codex.ai.monitoring.core.utils.UiText
import com.claude.codex.ai.monitoring.core.utils.resolve
import com.claude.codex.ai.monitoring.presentation.sessiondetail.components.TimelineLoading
import org.koin.compose.viewmodel.koinViewModel

/** "Projects on this computer": watch a project and open its History from the phone. */
@Composable
fun ComputerProjectsScreen(
    onBack: () -> Unit,
    onOpenHistory: (projectId: String, projectName: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ComputerProjectsViewModel = koinViewModel(),
) {
    val state by viewModel.computerProjectsUiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ComputerProjectsEffect.OpenHistory -> onOpenHistory(effect.projectId, effect.projectName)
            }
        }
    }

    AuroraBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxSize()
                .systemBarsPadding()
                .widthIn(max = Dimens.ContentMaxWidth),
        ) {
            AppTopBar(title = stringResource(R.string.computer_projects_title), onBack = onBack)
            when {
                state.isLoading && state.projects.isEmpty() -> TimelineLoading(modifier = Modifier.padding(horizontal = Dimens.ScreenPadding))
                state.isUnavailable -> StateMessage(
                    icon = R.drawable.ic_offline,
                    title = stringResource(R.string.computer_projects_unavailable_title),
                    message = stringResource(R.string.history_unavailable_message),
                    tone = StatusTone.ERROR,
                    actionLabel = stringResource(R.string.action_retry),
                    actionIcon = R.drawable.ic_refresh,
                    onAction = { viewModel.onEvent(ComputerProjectsEvent.OnRetryClick) },
                    modifier = Modifier.fillMaxWidth(),
                )
                state.notAllowed -> StateMessage(
                    icon = R.drawable.ic_shield,
                    title = stringResource(R.string.computer_projects_not_allowed_title),
                    message = stringResource(R.string.computer_projects_not_allowed_message),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.projects.isEmpty() -> StateMessage(
                    icon = R.drawable.ic_terminal,
                    title = stringResource(R.string.computer_projects_empty_title),
                    message = stringResource(R.string.computer_projects_empty_message),
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, bottom = Dimens.SpaceXxl),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                ) {
                    item(key = "hint", contentType = "hint") {
                        Text(
                            text = stringResource(if (state.canAdd) R.string.computer_projects_hint else R.string.computer_projects_hint_read_only),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textSecondary,
                        )
                    }
                    state.error?.let { error ->
                        item(key = "error", contentType = "hint") {
                            Text(error.resolve(), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.error)
                        }
                    }
                    items(state.projects, key = { it.projectId }, contentType = { "project" }) { project ->
                        ComputerProjectRow(
                            project = project,
                            adding = state.addingId == project.projectId,
                            canAdd = state.canAdd && state.addingId == null,
                            onClick = { viewModel.onEvent(ComputerProjectsEvent.OnProjectClick(project.projectId)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComputerProjectRow(
    project: ComputerProjectItemUiModel,
    adding: Boolean,
    canAdd: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SurfaceCard(onClick = onClick.takeIf { project.monitored || canAdd }, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = project.pathHint,
                    style = MonoTextStyle,
                    color = AppTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = project.detail.resolve(), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
            }
            when {
                project.monitored -> StatusPill(tone = StatusTone.DONE, label = stringResource(R.string.computer_projects_watching), animateOrb = false)
                adding -> StatusPill(tone = StatusTone.RUNNING, label = stringResource(R.string.computer_projects_adding), animateOrb = true)
                canAdd -> QuickActionChip(text = stringResource(R.string.computer_projects_watch), onClick = onClick)
            }
        }
    }
}

@Preview
@Composable
private fun ComputerProjectRowPreview() {
    AppTheme(darkTheme = true) {
        ComputerProjectRow(
            project = ComputerProjectItemUiModel("p1", "ShopKart", "OtherProjects\\ShopKart", UiText.Raw("12 conversations · 2 h ago"), monitored = false),
            adding = false,
            canAdd = true,
            onClick = {},
        )
    }
}
