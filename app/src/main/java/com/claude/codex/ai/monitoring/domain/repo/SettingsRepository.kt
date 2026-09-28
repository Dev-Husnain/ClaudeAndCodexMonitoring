package com.claude.codex.ai.monitoring.domain.repo

import com.claude.codex.ai.monitoring.domain.models.AppSettingsModel
import com.claude.codex.ai.monitoring.domain.models.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettingsModel>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setHapticsEnabled(enabled: Boolean)
}
