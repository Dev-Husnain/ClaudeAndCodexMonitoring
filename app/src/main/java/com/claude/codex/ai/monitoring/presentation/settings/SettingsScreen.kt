package com.claude.codex.ai.monitoring.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.claude.codex.ai.monitoring.core.ui.InfoRow
import com.claude.codex.ai.monitoring.presentation.settings.components.SettingsNavRow
import com.claude.codex.ai.monitoring.presentation.settings.components.SettingsSection
import com.claude.codex.ai.monitoring.presentation.settings.components.SettingsSwitchRow
import com.claude.codex.ai.monitoring.presentation.settings.components.ThemeModeSelector
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onDevicesClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.settingsUiState.collectAsStateWithLifecycle()

    AuroraBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxSize()
                .systemBarsPadding()
                .widthIn(max = Dimens.ContentMaxWidth),
        ) {
            AppTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, bottom = Dimens.SpaceXxl),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXxl),
            ) {
                SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
                    ThemeModeSelector(
                        options = state.themeOptions,
                        onSelect = { viewModel.onEvent(SettingsEvent.OnThemeModeSelect(it)) },
                    )
                }
                SettingsSection(title = stringResource(R.string.settings_section_security)) {
                    SettingsNavRow(
                        icon = R.drawable.ic_shield,
                        title = stringResource(R.string.devices_title),
                        summary = stringResource(R.string.settings_devices_summary),
                        onClick = onDevicesClick,
                    )
                }
                SettingsSection(title = stringResource(R.string.settings_section_feedback)) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_haptics),
                        summary = stringResource(R.string.settings_haptics_summary),
                        checked = state.hapticsEnabled,
                        onCheckedChange = { viewModel.onEvent(SettingsEvent.OnHapticsToggle(it)) },
                    )
                }
                SettingsSection(title = stringResource(R.string.settings_section_about)) {
                    InfoRow(label = stringResource(R.string.settings_version), value = state.appVersion)
                }
            }
        }
    }
}
