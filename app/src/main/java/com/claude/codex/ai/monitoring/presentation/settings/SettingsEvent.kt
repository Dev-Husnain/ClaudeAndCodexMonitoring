package com.claude.codex.ai.monitoring.presentation.settings

import com.claude.codex.ai.monitoring.domain.models.ThemeMode

sealed interface SettingsEvent {
    data class OnThemeModeSelect(val mode: ThemeMode) : SettingsEvent

    data class OnHapticsToggle(val enabled: Boolean) : SettingsEvent

    /** Sent with `true` only once notifications are allowed (the screen asks first). */
    data class OnBackgroundAlertsToggle(val enabled: Boolean) : SettingsEvent

    data object OnNotificationPermissionDenied : SettingsEvent
}
