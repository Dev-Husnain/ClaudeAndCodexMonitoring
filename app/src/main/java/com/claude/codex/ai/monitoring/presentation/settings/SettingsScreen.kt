package com.claude.codex.ai.monitoring.presentation.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.ui.AppTopBar
import com.claude.codex.ai.monitoring.core.ui.AuroraBackground
import com.claude.codex.ai.monitoring.core.ui.InfoBanner
import com.claude.codex.ai.monitoring.core.ui.InfoRow
import com.claude.codex.ai.monitoring.core.ui.StatusTone
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
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onEvent(if (granted) SettingsEvent.OnBackgroundAlertsToggle(true) else SettingsEvent.OnNotificationPermissionDenied)
    }
    val onAlertsToggle: (Boolean) -> Unit = { enabled ->
        val needsPermission = enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.onEvent(SettingsEvent.OnBackgroundAlertsToggle(enabled))
        }
    }
    // Battery and autostart live on the app's system page (Xiaomi adds "Autostart" there).
    val openAppSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
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
                SettingsSection(title = stringResource(R.string.settings_section_notifications)) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_background_alerts),
                        summary = stringResource(R.string.settings_background_alerts_summary),
                        checked = state.backgroundAlerts,
                        onCheckedChange = onAlertsToggle,
                    )
                    if (state.notificationsBlocked) {
                        InfoBanner(
                            icon = R.drawable.ic_alert,
                            text = stringResource(R.string.settings_notifications_blocked),
                            tone = StatusTone.ERROR,
                        )
                    }
                    if (state.backgroundAlerts || state.notificationsBlocked) {
                        SettingsNavRow(
                            icon = R.drawable.ic_settings,
                            title = stringResource(R.string.settings_battery),
                            summary = stringResource(R.string.settings_battery_summary),
                            onClick = openAppSettings,
                        )
                    }
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
