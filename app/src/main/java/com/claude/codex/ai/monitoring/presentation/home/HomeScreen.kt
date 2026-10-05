package com.claude.codex.ai.monitoring.presentation.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.ui.AuroraBackground
import com.claude.codex.ai.monitoring.core.ui.AwayModeToggle
import com.claude.codex.ai.monitoring.core.ui.ConfirmDialog
import androidx.compose.foundation.layout.padding
import com.claude.codex.ai.monitoring.core.ui.StateMessage
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.utils.resolve
import com.claude.codex.ai.monitoring.presentation.home.components.HomeHeader
import com.claude.codex.ai.monitoring.presentation.home.components.HomeLoading
import com.claude.codex.ai.monitoring.presentation.home.components.HomeSessionList
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    onSessionClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: (projectId: String, projectName: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.homeUiState.collectAsStateWithLifecycle()

    if (state.removeTarget != null) {
        ConfirmDialog(
            title = stringResource(R.string.remove_session_title),
            message = stringResource(R.string.remove_session_message),
            confirmLabel = stringResource(R.string.remove_session_confirm),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = { viewModel.onEvent(HomeEvent.OnRemoveConfirm) },
            onDismiss = { viewModel.onEvent(HomeEvent.OnRemoveDismiss) },
            destructive = true,
        )
    }

    state.removeProjectTarget?.let { project ->
        ConfirmDialog(
            title = stringResource(R.string.remove_project_title, project.name),
            message = stringResource(R.string.remove_project_message),
            confirmLabel = stringResource(R.string.remove_session_confirm),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = { viewModel.onEvent(HomeEvent.OnRemoveProjectConfirm) },
            onDismiss = { viewModel.onEvent(HomeEvent.OnRemoveDismiss) },
            destructive = true,
        )
    }

    AuroraBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxSize()
                .systemBarsPadding()
                .widthIn(max = Dimens.ContentMaxWidth),
        ) {
            HomeHeader(
                computerName = state.computerName,
                connection = state.connection,
                readOnly = state.readOnly,
                onSettingsClick = onSettingsClick,
            )
            if (state.canControl) {
                AwayModeToggle(
                    enabled = state.awayMode,
                    busy = state.awayBusy,
                    onToggle = { viewModel.onEvent(HomeEvent.OnAwayModeToggle(it)) },
                    modifier = Modifier.padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, bottom = Dimens.SpaceMd),
                )
            }
            val unauthorized = state.unauthorized
            when {
                unauthorized != null -> StateMessage(
                    icon = R.drawable.ic_shield,
                    title = stringResource(unauthorized.title),
                    message = unauthorized.message.resolve(),
                    tone = StatusTone.ERROR,
                    actionLabel = stringResource(R.string.action_pair_again),
                    actionIcon = R.drawable.ic_scan,
                    onAction = { viewModel.onEvent(HomeEvent.OnPairAgainClick) },
                    modifier = Modifier.fillMaxWidth(),
                )
                state.isLoading -> HomeLoading()
                state.isOffline -> StateMessage(
                    icon = R.drawable.ic_offline,
                    title = stringResource(R.string.home_offline_title),
                    message = state.offlineMessage?.resolve().orEmpty(),
                    tone = StatusTone.ERROR,
                    actionLabel = stringResource(R.string.action_retry),
                    actionIcon = R.drawable.ic_refresh,
                    onAction = { viewModel.onEvent(HomeEvent.OnRetryClick) },
                    modifier = Modifier.fillMaxWidth(),
                )
                state.isEmpty -> StateMessage(
                    icon = R.drawable.ic_terminal,
                    title = stringResource(R.string.home_empty_title),
                    message = stringResource(R.string.home_empty_message),
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> HomeSessionList(
                    needsYou = state.needsYou,
                    projects = state.projects,
                    staleNotice = state.staleNotice,
                    onSessionClick = onSessionClick,
                    onSessionLongClick = { viewModel.onEvent(HomeEvent.OnRemoveRequest(it)) },
                    onHistoryClick = onHistoryClick,
                    onRemoveProjectClick = { viewModel.onEvent(HomeEvent.OnRemoveProjectRequest(it)) },
                )
            }
        }
    }
}
