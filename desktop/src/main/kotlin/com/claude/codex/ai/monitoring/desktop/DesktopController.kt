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
import com.claude.codex.ai.monitoring.desktop.projects.ProjectStore
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
    val publicUrl: String,
    val demoMode: Boolean,
    val projects: ProjectStore,
    val tracker: SessionTracker,
    private val installer: HookInstaller,
    val control: ControlCenter,
    private val scope: CoroutineScope,
    val computer: ComputerOptions,
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
        return runCatching {
            installer.install(dir)
            val project = projects.add(dir)
            registry.upsertProject(ProjectDto(project.projectId, project.name))
            scope.launch { hub.reconnectAll() }
            audit.record(AuditCategory.ACCESS, "Monitoring \"${project.name}\" (started with agentmon claude)")
            project.projectId
        }.onFailure { _projectError.value = "Could not monitor ${dir.fileName}: ${it.message}" }.getOrNull()
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

    fun baseUrlFor(route: PairingRoute): String = when (route) {
        PairingRoute.TUNNEL -> publicUrl
        PairingRoute.USB -> "http://${ProtocolConstants.LOOPBACK_HOST}:${ProtocolConstants.DEFAULT_PORT}"
    }

    /**
     * Opens the pair dialog on the route that can actually work: the tunnel if it answers right now,
     * otherwise USB. A QR pointing at an unreachable address could only ever fail on the phone.
     */
    fun startPairing() {
        scope.launch {
            val reachable = probeTunnel()
            _tunnelReachable.value = reachable
            startPairing(if (reachable) PairingRoute.TUNNEL else PairingRoute.USB)
        }
    }

    fun startPairing(route: PairingRoute) {
        _route.value = route
        pairing.createOffer(baseUrlFor(route))
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
        const val PROBE_TIMEOUT_S = 4L
        const val HTTP_OK = 200
    }
}
