package com.claude.codex.ai.monitoring.domain.models

data class AppSettingsModel(
    val themeMode: ThemeMode,
    val hapticsEnabled: Boolean,
    /** Keep the connection open in the background and notify when Claude needs the user (M7). */
    val backgroundAlerts: Boolean = false,
)
