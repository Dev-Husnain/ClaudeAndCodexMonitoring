package com.claude.codex.ai.monitoring.data.repo

import com.claude.codex.ai.monitoring.data.local.SettingsDataSource
import com.claude.codex.ai.monitoring.domain.models.AppSettingsModel
import com.claude.codex.ai.monitoring.domain.models.ThemeMode
import com.claude.codex.ai.monitoring.domain.repo.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class SettingsRepositoryImpl(
    private val dataSource: SettingsDataSource,
) : SettingsRepository {

    override val settings: Flow<AppSettingsModel> = dataSource.preferences
        .map { prefs ->
            AppSettingsModel(
                themeMode = prefs[SettingsDataSource.Keys.ThemeMode]
                    ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                    ?: ThemeMode.SYSTEM,
                hapticsEnabled = prefs[SettingsDataSource.Keys.HapticsEnabled] ?: true,
            )
        }
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataSource.edit { it[SettingsDataSource.Keys.ThemeMode] = mode.name }
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        dataSource.edit { it[SettingsDataSource.Keys.HapticsEnabled] = enabled }
    }
}
