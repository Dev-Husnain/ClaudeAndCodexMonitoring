package com.claude.codex.ai.monitoring.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.claude.codex.ai.monitoring.domain.models.PairingModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.pairingDataStore: DataStore<Preferences> by preferencesDataStore(name = "pairing")

/** The pinned computer. Excluded from backups (see backup_rules.xml), so it never leaves the phone. */
class PairingDataSource(context: Context) {

    private val dataStore = context.pairingDataStore

    val pairing: Flow<PairingModel?> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            PairingModel(
                baseUrl = prefs[BaseUrl] ?: return@map null,
                deviceId = prefs[DeviceId] ?: return@map null,
                deviceName = prefs[DeviceName].orEmpty(),
                desktopPublicKey = prefs[DesktopPublicKey] ?: return@map null,
                desktopFingerprint = prefs[DesktopFingerprint] ?: return@map null,
                computerName = prefs[ComputerName].orEmpty(),
                canSendInput = prefs[CanSendInput] ?: false,
                pairedAtMs = prefs[PairedAt] ?: 0L,
            )
        }

    suspend fun save(model: PairingModel) {
        dataStore.edit { prefs ->
            prefs[BaseUrl] = model.baseUrl
            prefs[DeviceId] = model.deviceId
            prefs[DeviceName] = model.deviceName
            prefs[DesktopPublicKey] = model.desktopPublicKey
            prefs[DesktopFingerprint] = model.desktopFingerprint
            prefs[ComputerName] = model.computerName
            prefs[CanSendInput] = model.canSendInput
            prefs[PairedAt] = model.pairedAtMs
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val BaseUrl = stringPreferencesKey("base_url")
        val DeviceId = stringPreferencesKey("device_id")
        val DeviceName = stringPreferencesKey("device_name")
        val DesktopPublicKey = stringPreferencesKey("desktop_public_key")
        val DesktopFingerprint = stringPreferencesKey("desktop_fingerprint")
        val ComputerName = stringPreferencesKey("computer_name")
        val CanSendInput = booleanPreferencesKey("can_send_input")
        val PairedAt = longPreferencesKey("paired_at")
    }
}
