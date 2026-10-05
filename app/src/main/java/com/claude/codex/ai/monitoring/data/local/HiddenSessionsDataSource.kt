package com.claude.codex.ai.monitoring.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.hiddenSessionsDataStore: DataStore<Preferences> by preferencesDataStore(name = "hidden_sessions")

/** Removed sessions as "id|removedAtMs" entries; at most [MAX_ENTRIES], the oldest are dropped. */
class HiddenSessionsDataSource(context: Context) {

    private val dataStore = context.hiddenSessionsDataStore

    val hidden: Flow<Map<String, Long>> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> prefs[Entries].orEmpty().mapNotNull(::parse).toMap() }

    suspend fun hide(ids: Collection<String>, atMs: Long) {
        dataStore.edit { prefs ->
            val entries = prefs[Entries].orEmpty().mapNotNull(::parse).toMap() + ids.associateWith { atMs }
            prefs[Entries] = entries.entries
                .sortedByDescending { it.value }
                .take(MAX_ENTRIES)
                .mapTo(HashSet()) { "${it.key}$SEPARATOR${it.value}" }
        }
    }

    private fun parse(entry: String): Pair<String, Long>? {
        val id = entry.substringBeforeLast(SEPARATOR, "").takeIf { it.isNotEmpty() } ?: return null
        val at = entry.substringAfterLast(SEPARATOR).toLongOrNull() ?: return null
        return id to at
    }

    private companion object {
        val Entries = stringSetPreferencesKey("hidden")
        const val SEPARATOR = '|'
        const val MAX_ENTRIES = 500
    }
}
