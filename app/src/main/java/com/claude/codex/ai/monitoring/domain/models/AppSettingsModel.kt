package com.claude.codex.ai.monitoring.domain.models

data class AppSettingsModel(
    val themeMode: ThemeMode,
    val serverUrl: String,
    val hapticsEnabled: Boolean,
    val deviceId: String,
)
