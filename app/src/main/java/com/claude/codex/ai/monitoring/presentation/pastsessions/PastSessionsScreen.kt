package com.claude.codex.ai.monitoring.presentation.pastsessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.ui.GradientButton
import com.claude.codex.ai.monitoring.core.ui.AppTopBar
import com.claude.codex.ai.monitoring.core.ui.AuroraBackground
import com.claude.codex.ai.monitoring.core.ui.ConfirmDialog
import com.claude.codex.ai.monitoring.core.ui.StateMessage
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.utils.resolve
import com.claude.codex.ai.monitoring.presentation.pastsessions.components.PastSessionRow
import com.claude.codex.ai.monitoring.presentation.pastsessions.components.ResumeDialog
import com.claude.codex.ai.monitoring.presentation.sessiondetail.components.TimelineLoading
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PastSessionsScreen(
    projectId: String,
    projectName: String,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PastSessionsViewModel = koinViewModel(key = "history-$projectId") { parametersOf(projectId, projectName) },
) {
    val state by viewModel.pastSessionsUiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PastSessionsEffect.OpenSession -> onOpenSession(effect.sessionId)
            }
        }
    }

    state.resumeTarget?.let { target ->
        ResumeDialog(
            title = target.title,
            text = state.resumeText,
            onTextChange = { viewModel.onEvent(PastSessionsEvent.OnResumeTextChange(it)) },
            onResume = { viewModel.onEvent(PastSessionsEvent.OnResumeConfirm) },
            onDismiss = { viewModel.onEvent(PastSessionsEvent.OnResumeDismiss) },
            onOpenTerminal = { viewModel.onEvent(PastSessionsEvent.OnResumeInTerminal) },
            resuming = state.resuming,
            error = state.resumeError?.resolve(),
        )
    }
    if (state.removeTarget != null) {
        ConfirmDialog(
            title = stringResource(R.string.remove_session_title),
            message = stringResource(R.string.remove_session_message),
            confirmLabel = stringResource(R.string.remove_session_confirm),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = { viewModel.onEvent(PastSessionsEvent.OnRemoveConfirm) },
            onDismiss = { viewModel.onEvent(PastSessionsEvent.OnRemoveDismiss) },
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
            AppTopBar(title = stringResource(R.string.history_title), subtitle = state.projectName, onBack = onBack)
            when {
                state.isLoading && state.sessions.isEmpty() -> TimelineLoading(modifier = Modifier.padding(horizontal = Dimens.ScreenPadding))
                state.isUnavailable -> StateMessage(
                    icon = R.drawable.ic_offline,
                    title = stringResource(R.string.history_unavailable_title),
                    message = stringResource(R.string.history_unavailable_message),
                    tone = StatusTone.ERROR,
                    actionLabel = stringResource(R.string.action_retry),
                    actionIcon = R.drawable.ic_refresh,
                    onAction = { viewModel.onEvent(PastSessionsEvent.OnRetryClick) },
                    modifier = Modifier.fillMaxWidth(),
                )
                state.sessions.isEmpty() -> StateMessage(
                    icon = R.drawable.ic_activity,
                    title = stringResource(R.string.history_empty_title),
                    message = state.startError?.resolve() ?: stringResource(R.string.history_empty_message),
                    actionLabel = if (state.canResume) stringResource(R.string.history_new_session) else null,
                    actionIcon = R.drawable.ic_play,
                    onAction = { viewModel.onEvent(PastSessionsEvent.OnNewSessionClick) },
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, bottom = Dimens.SpaceXxl),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                ) {
                    item(key = "hint", contentType = "hint") {
                        Text(
                            text = stringResource(if (state.canResume) R.string.history_hint else R.string.history_hint_read_only),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textSecondary,
                        )
                    }
                    if (state.canResume) {
                        item(key = "new-session", contentType = "action") {
                            GradientButton(
                                text = stringResource(if (state.startingTerminal) R.string.detail_terminal_starting else R.string.history_new_session),
                                onClick = { viewModel.onEvent(PastSessionsEvent.OnNewSessionClick) },
                                enabled = !state.startingTerminal,
                                leadingIcon = R.drawable.ic_play,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        state.startError?.let { error ->
                            item(key = "start-error", contentType = "hint") {
                                Text(error.resolve(), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.error)
                            }
                        }
                    }
                    items(state.sessions, key = { it.claudeSessionId }, contentType = { "session" }) { session ->
                        PastSessionRow(
                            session = session,
                            onClick = { viewModel.onEvent(PastSessionsEvent.OnSessionClick(session.claudeSessionId)) },
                            onLongClick = { viewModel.onEvent(PastSessionsEvent.OnSessionLongClick(session.claudeSessionId)) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}
