package com.claude.codex.ai.monitoring.presentation.sessiondetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.ui.AppTopBar
import com.claude.codex.ai.monitoring.core.ui.AuroraBackground
import com.claude.codex.ai.monitoring.core.ui.ConfirmDialog
import com.claude.codex.ai.monitoring.core.ui.StateMessage
import com.claude.codex.ai.monitoring.core.ui.StatusPill
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.utils.resolve
import com.claude.codex.ai.monitoring.domain.models.QuickActionType
import com.claude.codex.ai.monitoring.presentation.sessiondetail.components.DetailTabs
import com.claude.codex.ai.monitoring.presentation.sessiondetail.components.SessionActivityList
import com.claude.codex.ai.monitoring.presentation.sessiondetail.components.TerminalKeysRow
import com.claude.codex.ai.monitoring.presentation.sessiondetail.components.TerminalView
import com.claude.codex.ai.monitoring.presentation.sessiondetail.components.SessionComposer
import com.claude.codex.ai.monitoring.presentation.sessiondetail.components.StopClaudeButton
import com.claude.codex.ai.monitoring.presentation.sessiondetail.components.TimelineLoading
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionDetailViewModel = koinViewModel(key = sessionId) { parametersOf(sessionId) },
) {
    val state by viewModel.sessionDetailUiState.collectAsStateWithLifecycle()
    val terminal by viewModel.terminalUiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SessionDetailEffect.Haptic -> haptics.performHapticFeedback(
                    if (effect.success) HapticFeedbackType.Confirm else HapticFeedbackType.Reject,
                )
            }
        }
    }

    if (state.showStopConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.detail_stop_confirm_title),
            message = stringResource(R.string.detail_stop_confirm_message),
            confirmLabel = stringResource(R.string.detail_stop_confirm),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = { viewModel.onEvent(SessionDetailEvent.OnStopConfirm) },
            onDismiss = { viewModel.onEvent(SessionDetailEvent.OnStopDismiss) },
            destructive = true,
        )
    }

    AuroraBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .widthIn(max = Dimens.ContentMaxWidth),
        ) {
            AppTopBar(
                title = state.title,
                subtitle = state.subtitle?.resolve(),
                onBack = onBack,
                actions = { StatusPill(tone = state.connection.tone, label = state.connection.label.resolve()) },
            )
            val header = state.header
            when {
                state.isLoading -> TimelineLoading(modifier = Modifier.padding(horizontal = Dimens.ScreenPadding))
                state.isNotFound || header == null -> StateMessage(
                    icon = R.drawable.ic_alert,
                    title = stringResource(R.string.detail_not_found_title),
                    message = stringResource(R.string.detail_not_found_message),
                    tone = StatusTone.STALE,
                    actionLabel = if (state.isOffline) stringResource(R.string.action_retry) else stringResource(R.string.action_back),
                    actionIcon = if (state.isOffline) R.drawable.ic_refresh else R.drawable.ic_back,
                    onAction = if (state.isOffline) {
                        { viewModel.onEvent(SessionDetailEvent.OnRetryClick) }
                    } else {
                        onBack
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> {
                    if (state.hasTerminal) {
                        DetailTabs(
                            selected = state.tab,
                            onSelect = { viewModel.onEvent(SessionDetailEvent.OnTabSelect(it)) },
                            modifier = Modifier.padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, bottom = Dimens.SpaceMd),
                        )
                    }
                    if (state.hasTerminal && state.tab == DetailTab.TERMINAL) {
                        TerminalView(
                            screen = terminal.screen,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = Dimens.ScreenPadding),
                        )
                        if (state.canControl) {
                            TerminalKeysRow(
                                enabled = !state.isOffline,
                                onKey = { viewModel.onEvent(SessionDetailEvent.OnTerminalKey(it)) },
                                modifier = Modifier.padding(top = Dimens.SpaceMd),
                            )
                        }
                    } else {
                        SessionActivityList(
                            header = header,
                            timeline = state.timeline,
                            isOffline = state.isOffline,
                            controls = state.controls(),
                            onApprove = { viewModel.onEvent(SessionDetailEvent.OnQuickAction(QuickActionType.APPROVE)) },
                            onDeny = { viewModel.onEvent(SessionDetailEvent.OnQuickAction(QuickActionType.DENY)) },
                            onStop = { viewModel.onEvent(SessionDetailEvent.OnQuickAction(QuickActionType.INTERRUPT)) },
                            onContinue = { viewModel.onEvent(SessionDetailEvent.OnQuickAction(QuickActionType.CONTINUE)) },
                            onAwayModeToggle = { viewModel.onEvent(SessionDetailEvent.OnAwayModeToggle(it)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (state.canStop) {
                        StopClaudeButton(
                            onClick = { viewModel.onEvent(SessionDetailEvent.OnStopClick) },
                            enabled = !state.sending && !state.isOffline,
                            modifier = Modifier.padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, top = Dimens.SpaceMd),
                        )
                    }
                    if (state.showComposer) {
                        SessionComposer(
                            text = state.composerText,
                            onTextChange = { viewModel.onEvent(SessionDetailEvent.OnComposerChange(it)) },
                            onSend = { viewModel.onEvent(SessionDetailEvent.OnSendClick) },
                            sending = state.sending,
                            note = state.deliveryNote?.resolve() ?: when {
                                state.resumable -> stringResource(R.string.detail_resume_hint)
                                state.startedFromPhone -> stringResource(R.string.detail_headless_hint)
                                state.hasTerminal -> stringResource(R.string.detail_terminal_hint)
                                !state.awayMode && state.awaiting == null -> stringResource(R.string.detail_away_hint)
                                else -> null
                            },
                            noteTone = state.deliveryTone,
                        )
                    }
                }
            }
        }
    }
}
