package com.claude.codex.ai.monitoring.presentation.sessiondetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.claude.codex.ai.monitoring.core.ui.AppTopBar
import com.claude.codex.ai.monitoring.core.ui.AuroraBackground
import com.claude.codex.ai.monitoring.core.ui.StateMessage
import com.claude.codex.ai.monitoring.core.ui.StatusPill
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.utils.resolve
import com.claude.codex.ai.monitoring.presentation.sessiondetail.components.SessionActivityList
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

    AuroraBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxSize()
                .systemBarsPadding()
                .widthIn(max = Dimens.ContentMaxWidth),
        ) {
            AppTopBar(
                title = state.title,
                subtitle = state.subtitle,
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
                else -> SessionActivityList(
                    header = header,
                    timeline = state.timeline,
                    isOffline = state.isOffline,
                )
            }
        }
    }
}
