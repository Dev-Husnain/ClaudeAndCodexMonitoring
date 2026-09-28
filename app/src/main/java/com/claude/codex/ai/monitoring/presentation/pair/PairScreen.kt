package com.claude.codex.ai.monitoring.presentation.pair

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.utils.resolve
import com.claude.codex.ai.monitoring.presentation.pair.components.CodeEntryPanel
import com.claude.codex.ai.monitoring.presentation.pair.components.PairConfirmPanel
import com.claude.codex.ai.monitoring.presentation.pair.components.PairSuccessPanel
import com.claude.codex.ai.monitoring.presentation.pair.components.PairWaitingPanel
import com.claude.codex.ai.monitoring.presentation.pair.components.QrScannerPanel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PairScreen(
    onBack: () -> Unit,
    onPaired: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PairViewModel = koinViewModel(),
) {
    val state by viewModel.pairUiState.collectAsStateWithLifecycle()

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
                title = stringResource(R.string.pair_title),
                onBack = if (state.step is PairStep.Success) null else onBack,
            )
            AnimatedContent(
                targetState = state.step,
                contentKey = { it::class },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "pairStep",
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = Dimens.SpaceXxl),
            ) { step ->
                when (step) {
                    PairStep.Scan -> if (state.showCodeEntry) {
                        CodeEntryPanel(
                            value = state.codeInput,
                            error = state.codeError,
                            onValueChange = { viewModel.onEvent(PairEvent.OnCodeInputChange(it)) },
                            onSubmit = { viewModel.onEvent(PairEvent.OnCodeSubmit) },
                            onScanClick = { viewModel.onEvent(PairEvent.OnToggleCodeEntry) },
                        )
                    } else {
                        QrScannerPanel(
                            onCodeScanned = { viewModel.onEvent(PairEvent.OnCodeScanned(it)) },
                            onEnterCodeClick = { viewModel.onEvent(PairEvent.OnToggleCodeEntry) },
                        )
                    }
                    is PairStep.Confirm -> PairConfirmPanel(
                        offer = step.offer,
                        deviceName = state.deviceName,
                        nameError = state.nameError,
                        onDeviceNameChange = { viewModel.onEvent(PairEvent.OnDeviceNameChange(it)) },
                        onSend = { viewModel.onEvent(PairEvent.OnSendRequest) },
                    )
                    is PairStep.Waiting -> PairWaitingPanel(computerName = step.computerName)
                    is PairStep.Success -> PairSuccessPanel(
                        computerName = step.computerName,
                        canSendInput = step.canSendInput,
                        onContinue = onPaired,
                    )
                    is PairStep.Failed -> StateMessage(
                        icon = R.drawable.ic_alert,
                        title = stringResource(R.string.pair_error_title),
                        message = step.message.resolve(),
                        tone = StatusTone.ERROR,
                        actionLabel = stringResource(R.string.pair_error_retry),
                        actionIcon = R.drawable.ic_scan,
                        onAction = { viewModel.onEvent(PairEvent.OnRetry) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
