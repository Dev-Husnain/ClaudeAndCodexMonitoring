package com.claude.codex.ai.monitoring.presentation.devicessecurity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.claude.codex.ai.monitoring.core.ui.ConfirmDialog
import com.claude.codex.ai.monitoring.core.ui.InfoBanner
import com.claude.codex.ai.monitoring.core.ui.InfoRow
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.ui.SurfaceCard
import com.claude.codex.ai.monitoring.core.ui.SectionHeader
import com.claude.codex.ai.monitoring.core.ui.GradientButton
import org.koin.compose.viewmodel.koinViewModel

/** This phone's identity, the pinned computer, and how access is removed (spec 9.3 #5). */
@Composable
fun DevicesSecurityScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DevicesSecurityViewModel = koinViewModel(),
) {
    val state by viewModel.devicesSecurityUiState.collectAsStateWithLifecycle()

    AuroraBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxSize()
                .systemBarsPadding()
                .widthIn(max = Dimens.ContentMaxWidth),
        ) {
            AppTopBar(title = stringResource(R.string.devices_title), onBack = onBack)
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, bottom = Dimens.SpaceXxl),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
            ) {
                SectionHeader(title = stringResource(R.string.devices_section_computer))
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                        InfoRow(label = stringResource(R.string.devices_computer_name), value = state.computerName)
                        InfoRow(label = stringResource(R.string.devices_address), value = state.address)
                        InfoRow(label = stringResource(R.string.devices_desktop_key), value = state.desktopKey)
                        InfoRow(label = stringResource(R.string.devices_access), value = stringResource(state.access))
                        InfoRow(label = stringResource(R.string.devices_paired_on), value = state.pairedOn)
                    }
                }
                SectionHeader(title = stringResource(R.string.devices_section_phone), modifier = Modifier.padding(top = Dimens.SpaceMd))
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                        InfoRow(label = stringResource(R.string.devices_phone_name), value = state.phoneName)
                        InfoRow(label = stringResource(R.string.devices_phone_key), value = state.phoneKey)
                        InfoRow(label = stringResource(R.string.devices_key_storage), value = stringResource(R.string.devices_key_storage_value))
                    }
                }
                SectionHeader(title = stringResource(R.string.devices_section_revoke), modifier = Modifier.padding(top = Dimens.SpaceMd))
                InfoBanner(icon = R.drawable.ic_shield, text = stringResource(R.string.devices_revoke_help), tone = StatusTone.BRAND)
                GradientButton(
                    text = stringResource(R.string.devices_unpair),
                    onClick = { viewModel.onEvent(DevicesSecurityEvent.OnUnpairClick) },
                    leadingIcon = R.drawable.ic_unlink,
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpaceSm),
                )
            }
        }
    }

    if (state.showUnpairConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.devices_unpair_confirm_title),
            message = stringResource(R.string.devices_unpair_confirm_message, state.computerName),
            confirmLabel = stringResource(R.string.devices_unpair_confirm),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = { viewModel.onEvent(DevicesSecurityEvent.OnUnpairConfirm) },
            onDismiss = { viewModel.onEvent(DevicesSecurityEvent.OnUnpairDismiss) },
            destructive = true,
        )
    }
}
