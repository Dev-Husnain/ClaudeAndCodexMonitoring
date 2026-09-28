package com.claude.codex.ai.monitoring.fakes

import com.claude.codex.ai.monitoring.domain.models.AppSettingsModel
import com.claude.codex.ai.monitoring.domain.models.ThemeMode
import com.claude.codex.ai.monitoring.domain.repo.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(
    initial: AppSettingsModel = AppSettingsModel(ThemeMode.SYSTEM, "ws://127.0.0.1:8787/ws", true, "device-1"),
) : SettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun setThemeMode(mode: ThemeMode) = settings.update { it.copy(themeMode = mode) }

    override suspend fun setServerUrl(url: String) = settings.update { it.copy(serverUrl = url) }

    override suspend fun setHapticsEnabled(enabled: Boolean) = settings.update { it.copy(hapticsEnabled = enabled) }
}
