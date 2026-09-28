package com.claude.codex.ai.monitoring.desktop.devices

import com.claude.codex.ai.monitoring.desktop.db.AgentMonDatabase
import com.claude.codex.ai.monitoring.desktop.db.Device
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a paired phone may see and do (spec 6.2 grant options, 6.4 isolation). */
data class DeviceGrant(
    val canSendInput: Boolean,
    val allProjects: Boolean,
    val projectIds: Set<String>,
) {
    fun allows(projectId: String): Boolean = allProjects || projectId in projectIds
}

data class PairedDevice(
    val deviceId: String,
    val name: String,
    val publicKey: String,
    val fingerprint: String,
    val grant: DeviceGrant,
    val pairedAtMs: Long,
    val lastSeenAtMs: Long?,
)

/** Persistent registry of paired phones. [devices] mirrors the table for the UI. */
class DeviceStore(
    private val database: AgentMonDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val queries = database.deviceQueries
    private val _devices = MutableStateFlow(load())
    val devices: StateFlow<List<PairedDevice>> = _devices.asStateFlow()

    fun find(deviceId: String): PairedDevice? = queries.selectById(deviceId).executeAsOneOrNull()?.toPairedDevice()

    fun save(device: PairedDevice) = write {
        queries.upsert(
            device_id = device.deviceId,
            name = device.name,
            public_key = device.publicKey,
            fingerprint = device.fingerprint,
            can_send_input = device.grant.canSendInput.toLong(),
            all_projects = device.grant.allProjects.toLong(),
            project_ids = device.grant.projectIds.joinToString(SEPARATOR),
            paired_at = device.pairedAtMs,
            last_seen_at = device.lastSeenAtMs,
        )
    }

    fun updateGrant(deviceId: String, grant: DeviceGrant) = write {
        queries.updateGrant(grant.canSendInput.toLong(), grant.allProjects.toLong(), grant.projectIds.joinToString(SEPARATOR), deviceId)
    }

    fun markSeen(deviceId: String) = write { queries.updateLastSeen(clock(), deviceId) }

    fun remove(deviceId: String) = write { queries.deleteById(deviceId) }

    private fun write(block: () -> Unit) {
        synchronized(this) {
            block()
            _devices.value = load()
        }
    }

    private fun load(): List<PairedDevice> = queries.selectAll().executeAsList().map { it.toPairedDevice() }

    private fun Device.toPairedDevice() = PairedDevice(
        deviceId = device_id,
        name = name,
        publicKey = public_key,
        fingerprint = fingerprint,
        grant = DeviceGrant(
            canSendInput = can_send_input != 0L,
            allProjects = all_projects != 0L,
            projectIds = project_ids.split(SEPARATOR).filter { it.isNotBlank() }.toSet(),
        ),
        pairedAtMs = paired_at,
        lastSeenAtMs = last_seen_at,
    )

    private fun Boolean.toLong() = if (this) 1L else 0L

    private companion object {
        const val SEPARATOR = "\n"
    }
}
