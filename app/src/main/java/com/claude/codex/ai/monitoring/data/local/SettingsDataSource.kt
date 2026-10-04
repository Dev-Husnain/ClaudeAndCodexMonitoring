package com.claude.codex.ai.monitoring.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataSource(context: Context) {

    private val dataStore = context.settingsDataStore

    val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    suspend fun edit(transform: (MutablePreferences) -> Unit) {
        dataStore.edit { transform(it) }
    }

    object Keys {
        val ThemeMode = stringPreferencesKey("theme_mode")
        val HapticsEnabled = booleanPreferencesKey("haptics_enabled")
        val BackgroundAlerts = booleanPreferencesKey("background_alerts")
    }
}
