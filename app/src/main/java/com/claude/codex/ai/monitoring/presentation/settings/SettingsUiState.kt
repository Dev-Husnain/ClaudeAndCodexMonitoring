package com.claude.codex.ai.monitoring.presentation.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.domain.models.ThemeMode

@Immutable
data class SettingsUiState(
    val themeOptions: List<ThemeOptionUiModel> = ThemeMode.entries.map { ThemeOptionUiModel(it, it.labelRes(), it == ThemeMode.SYSTEM) },
    val hapticsEnabled: Boolean = true,
    val appVersion: String = "",
    /** Alerts when the app is closed (foreground service). */
    val backgroundAlerts: Boolean = false,
    /** The user refused notifications: alerts cannot be shown until allowed in Android settings. */
    val notificationsBlocked: Boolean = false,
)

@Immutable
data class ThemeOptionUiModel(
    val mode: ThemeMode,
    @param:StringRes val label: Int,
    val selected: Boolean,
)

fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.DARK -> R.string.settings_theme_dark
    ThemeMode.LIGHT -> R.string.settings_theme_light
}
