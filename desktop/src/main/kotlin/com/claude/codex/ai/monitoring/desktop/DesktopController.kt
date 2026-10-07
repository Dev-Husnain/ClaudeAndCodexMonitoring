package com.claude.codex.ai.monitoring.desktop

import com.claude.codex.ai.monitoring.desktop.control.ControlCenter
import com.claude.codex.ai.monitoring.desktop.devices.AuditCategory
import com.claude.codex.ai.monitoring.desktop.devices.AuditLog
import com.claude.codex.ai.monitoring.desktop.devices.DeviceGrant
import com.claude.codex.ai.monitoring.desktop.devices.DeviceStore
import com.claude.codex.ai.monitoring.desktop.devices.PairedDevice
import com.claude.codex.ai.monitoring.desktop.pairing.PairingDecision
import com.claude.codex.ai.monitoring.desktop.pairing.PairingManager
import com.claude.codex.ai.monitoring.desktop.security.DesktopIdentity
import com.claude.codex.ai.monitoring.desktop.hooks.HookInstaller
import com.claude.codex.ai.monitoring.desktop.projects.MonitoredProject
import com.claude.codex.ai.monitoring.desktop.projects.ProjectDiscovery
import com.claude.codex.ai.monitoring.desktop.projects.ProjectStore
import com.claude.codex.ai.monitoring.desktop.control.Delivery
import com.claude.codex.ai.monitoring.desktop.control.ProjectAccess
import com.claude.codex.ai.monitoring.protocol.AvailableProjectDto
import com.claude.codex.ai.monitoring.protocol.DeliveryResult
import kotlinx.coroutines.delay
import com.claude.codex.ai.monitoring.desktop.server.ConnectionHub
import com.claude.codex.ai.monitoring.desktop.session.SessionTracker
import com.claude.codex.ai.monitoring.protocol.ProjectDto
import java.nio.file.Path
import com.claude.codex.ai.monitoring.desktop.session.SessionRegistry
import com.claude.codex.ai.monitoring.desktop.system.ComputerOptions
import com.claude.codex.ai.monitoring.protocol.ProtocolConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** How the phone will reach this computer; decides the address inside the pairing QR. */
enum class PairingRoute { TUNNEL, USB }

/** Actions the desktop UI can take. Anything that changes access applies to live connections at once. */
class DesktopController(
    val registry: SessionRegistry,
    val hub: ConnectionHub,
    val devices: DeviceStore,
    val audit: AuditLog,
    val pairing: PairingManager,
    val identity: DesktopIdentity,
    val demoMode: Boolean,
    val projects: ProjectStore,
    val tracker: SessionTracker,
    private val installer: HookInstaller,
    val control: ControlCenter,
    private val scope: CoroutineScope,
    val computer: ComputerOptions,
    private val discovery: ProjectDiscovery,
) {
    fun setAwayMode(enabled: Boolean) = control.setAwayMode(enabled, by = "desktop")

    /** Last problem adding or removing a project, shown on the Projects screen until the next action. */
    private val _projectError = MutableStateFlow<String?>(null)
    val projectError: StateFlow<String?> = _projectError.asStateFlow()

    fun hooksInstalled(project: MonitoredProject): Boolean = installer.isInstalled(Path.of(project.path))

    /** Monitors [dir]: installs the hooks into its local settings and shows it to granted phones. */
    fun addProject(dir: Path) {
        scope.launch {
            runCatching {
                installer.install(dir)
                val project = projects.add(dir)
                registry.upsertProject(ProjectDto(project.projectId, project.name))
                // Phones learn project names from `ready`; refresh them so the new project is named.
                hub.reconnectAll()
                audit.record(AuditCategory.ACCESS, "Monitoring \"${project.name}\" (hooks installed)")
            }.onSuccess { _projectError.value = null }
                .onFailure { _projectError.value = "Could not add ${dir.fileName}: ${it.message}" }
        }
    }

    /**
     * `agentmon claude` started in a folder that is not monitored yet. Starting it there is the request to see that
     * session on the phone, so the folder is added as "Add project" would (hooks included). Home folders and drive
     * roots are never added. Phones limited to chosen projects still do not see it until it is granted.
     * Returns the project id, or null when the folder stays unmonitored.
     */
    fun monitorForWrapper(cwd: String): String? {
        projects.projectFor(cwd)?.let { return it.projectId }
        val dir = runCatching { Path.of(cwd).toAbsolutePath().normalize() }.getOrNull() ?: return null
        if (!ProjectStore.canAutoMonitor(dir)) return null
        return monitor(dir, "started with agentmon claude").getOrNull()
    }

    /** Installs the hooks and watches [dir]; phones reconnect (a moment later, after any answer) to learn its name. */
    private fun monitor(dir: Path, how: String): Result<String> = runCatching {
        installer.install(dir)
        val project = projects.add(dir)
        registry.upsertProject(ProjectDto(project.projectId, project.name))
        scope.launch {
            delay(RECONNECT_AFTER_ADD_MS)
            hub.reconnectAll()
        }
        audit.record(AuditCategory.ACCESS, "Monitoring \"${project.name}\" ($how)")
        project.projectId
    }.onFailure { _projectError.value = "Could not monitor ${dir.fileName}: ${it.message}" }

    /** "Projects on this computer" for the phone. */
    val projectAccess: ProjectAccess = object : ProjectAccess {
        override fun available(): List<AvailableProjectDto> = discovery.discover().map { it.dto }

        override fun addFromPhone(projectId: String, by: String): Delivery {
            val found = discovery.find(projectId) ?: return Delivery(DeliveryResult.FAILED, "This project is no longer on this computer")
            if (found.dto.monitored) return Delivery(DeliveryResult.DELIVERED, found.dto.projectId)
            return monitor(found.path, "added from $by").fold(
                onSuccess = { Delivery(DeliveryResult.DELIVERED, it) },
                onFailure = { Delivery(DeliveryResult.FAILED, "Could not watch this project: ${it.message.orEmpty().take(200)}") },
            )
        }
    }

    fun reinstallHooks(project: MonitoredProject) {
        scope.launch {
            runCatching { installer.install(Path.of(project.path)) }
                .onSuccess { _projectError.value = null }
                .onFailure { _projectError.value = "Could not reinstall hooks for ${project.name}: ${it.message}" }
        }
    }

    /** Stops monitoring: removes only our hooks (the user's own hooks stay) and forgets the sessions. */
    fun removeProject(project: MonitoredProject) {
        scope.launch {
            runCatching { installer.uninstall(Path.of(project.path)) }
                .onFailure { _projectError.value = "Hooks for ${project.name} could not be removed: ${it.message}" }
            projects.remove(project.projectId)
            registry.removeProject(project.projectId)
            hub.reconnectAll()
            audit.record(AuditCategory.ACCESS, "Stopped monitoring \"${project.name}\" (hooks removed)")
        }
    }

    private val _route = MutableStateFlow(PairingRoute.USB)
    val route: StateFlow<PairingRoute> = _route.asStateFlow()

    /** Null while unknown (never checked or checking). */
    private val _tunnelReachable = MutableStateFlow<Boolean?>(null)
    val tunnelReachable: StateFlow<Boolean?> = _tunnelReachable.asStateFlow()

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(PROBE_TIMEOUT_S)).build()

    /** Null for the tunnel route while no address is set up. */
    fun baseUrlFor(route: PairingRoute): String? = when (route) {
        PairingRoute.TUNNEL -> computer.publicUrl.value
        PairingRoute.USB -> "http://${ProtocolConstants.LOOPBACK_HOST}:${ProtocolConstants.DEFAULT_PORT}"
    }

    /**
     * Opens the pair dialog on the tunnel whenever an address is set, and on USB otherwise. The tunnel is checked
     * from this computer only to warn: what matters is whether the phone reaches it, and this computer's own DNS
     * can fail while the phone's works (a QR silently switched to USB then fails on every phone without adb).
     */
    fun startPairing() {
        _tunnelReachable.value = null
        startPairing(if (computer.publicUrl.value != null) PairingRoute.TUNNEL else PairingRoute.USB)
        scope.launch { _tunnelReachable.value = probeTunnel() }
    }

    fun startPairing(route: PairingRoute) {
        // Without an address the tunnel route cannot work; the dialog explains where to set one.
        val effective = if (baseUrlFor(route) == null) PairingRoute.USB else route
        _route.value = effective
        pairing.createOffer(baseUrlFor(effective)!!)
    }

    fun cancelPairing() = pairing.cancelOffer()

    fun approve(grant: DeviceGrant) = pairing.decide(PairingDecision.Approve(grant))

    fun reject() = pairing.decide(PairingDecision.Reject)

    fun updateGrant(device: PairedDevice, grant: DeviceGrant) {
        scope.launch {
            devices.updateGrant(device.deviceId, grant)
            audit.record(AuditCategory.ACCESS, "Access changed for \"${device.name}\"", device.deviceId)
            hub.reconnect(device.deviceId)
        }
    }

    fun revoke(device: PairedDevice) {
        scope.launch {
            devices.remove(device.deviceId)
            audit.record(AuditCategory.ACCESS, "Revoked \"${device.name}\"", device.deviceId)
            hub.revoke(device.deviceId)
        }
    }

    /** True when the public tunnel address reaches this very agent (not just any server). */
    private suspend fun probeTunnel(): Boolean = withContext(Dispatchers.IO) {
        val publicUrl = computer.publicUrl.value ?: return@withContext false
        runCatching {
            val request = HttpRequest.newBuilder(URI(publicUrl + ProtocolConstants.PATH_HEALTH))
                .timeout(Duration.ofSeconds(PROBE_TIMEOUT_S))
                .GET()
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == HTTP_OK && response.body().contains("\"ok\"")
        }.getOrDefault(false)
    }

    private companion object {
        const val RECONNECT_AFTER_ADD_MS = 1_500L
        const val PROBE_TIMEOUT_S = 4L
        const val HTTP_OK = 200
    }
}
