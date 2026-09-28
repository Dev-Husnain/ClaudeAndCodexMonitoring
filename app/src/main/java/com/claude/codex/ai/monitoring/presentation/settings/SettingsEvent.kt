package com.claude.codex.ai.monitoring.presentation.settings

import com.claude.codex.ai.monitoring.domain.models.ThemeMode

sealed interface SettingsEvent {
    data class OnThemeModeSelect(val mode: ThemeMode) : SettingsEvent

    data class OnServerUrlChange(val value: String) : SettingsEvent

    data object OnServerUrlSave : SettingsEvent

    data class OnHapticsToggle(val enabled: Boolean) : SettingsEvent
}
