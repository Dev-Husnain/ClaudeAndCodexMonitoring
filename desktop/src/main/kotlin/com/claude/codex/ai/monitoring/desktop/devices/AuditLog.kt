package com.claude.codex.ai.monitoring.desktop.devices

import com.claude.codex.ai.monitoring.desktop.db.AgentMonDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AuditCategory { PAIRING, AUTH, ACCESS, SERVER }

data class AuditEntry(
    val id: Long,
    val timestampMs: Long,
    val category: AuditCategory,
    val message: String,
    val deviceId: String?,
    val remote: String?,
)

/** Security audit trail (spec 6.3). Holds metadata only, never prompt or terminal content. */
class AuditLog(
    database: AgentMonDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val queries = database.auditEntryQueries
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<AuditEntry>> = _entries.asStateFlow()

    fun record(category: AuditCategory, message: String, deviceId: String? = null, remote: String? = null) {
        synchronized(this) {
            queries.insert(clock(), category.name, message, deviceId, remote)
            queries.trimTo(MAX_STORED)
            _entries.value = load()
        }
    }

    /** Empties the log. One entry stays to show that, and when, it was cleared. */
    fun clear() {
        synchronized(this) {
            queries.deleteAll()
            queries.insert(clock(), AuditCategory.SERVER.name, "Activity log cleared", null, null)
            _entries.value = load()
        }
    }

    private fun load(): List<AuditEntry> = queries.selectRecent(MAX_SHOWN).executeAsList().map {
        AuditEntry(
            id = it.id,
            timestampMs = it.ts,
            category = AuditCategory.entries.firstOrNull { c -> c.name == it.category } ?: AuditCategory.SERVER,
            message = it.message,
            deviceId = it.device_id,
            remote = it.remote,
        )
    }

    private companion object {
        const val MAX_STORED = 5_000L
        const val MAX_SHOWN = 300L
    }
}
