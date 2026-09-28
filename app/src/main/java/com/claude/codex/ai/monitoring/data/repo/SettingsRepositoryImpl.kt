package com.claude.codex.ai.monitoring.data.repo

import com.claude.codex.ai.monitoring.data.local.SettingsDataSource
import com.claude.codex.ai.monitoring.domain.models.AppSettingsModel
import com.claude.codex.ai.monitoring.domain.models.ThemeMode
import com.claude.codex.ai.monitoring.domain.repo.SettingsRepository
import com.claude.codex.ai.monitoring.protocol.ProtocolConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import java.util.UUID

class SettingsRepositoryImpl(
    private val dataSource: SettingsDataSource,
) : SettingsRepository {

    override val settings: Flow<AppSettingsModel> = dataSource.preferences
        .onStart { ensureDeviceId() }
        .mapNotNull { prefs ->
            // Emits only once a device id exists, so consumers never see a blank identity.
            val deviceId = prefs[SettingsDataSource.Keys.DeviceId] ?: return@mapNotNull null
            AppSettingsModel(
                themeMode = prefs[SettingsDataSource.Keys.ThemeMode]
                    ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                    ?: ThemeMode.SYSTEM,
                serverUrl = prefs[SettingsDataSource.Keys.ServerUrl] ?: DEFAULT_SERVER_URL,
                hapticsEnabled = prefs[SettingsDataSource.Keys.HapticsEnabled] ?: true,
                deviceId = deviceId,
            )
        }
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataSource.edit { it[SettingsDataSource.Keys.ThemeMode] = mode.name }
    }

    override suspend fun setServerUrl(url: String) {
        dataSource.edit { it[SettingsDataSource.Keys.ServerUrl] = url }
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        dataSource.edit { it[SettingsDataSource.Keys.HapticsEnabled] = enabled }
    }

    private suspend fun ensureDeviceId() {
        dataSource.edit { prefs ->
            if (prefs[SettingsDataSource.Keys.DeviceId] == null) {
                prefs[SettingsDataSource.Keys.DeviceId] = UUID.randomUUID().toString()
            }
        }
    }

    private companion object {
        /** Loopback default: reaches the desktop agent through `adb reverse tcp:8787 tcp:8787`. */
        const val DEFAULT_SERVER_URL =
            "ws://${ProtocolConstants.LOOPBACK_HOST}:${ProtocolConstants.DEFAULT_PORT}${ProtocolConstants.PATH_WS}"
    }
}
