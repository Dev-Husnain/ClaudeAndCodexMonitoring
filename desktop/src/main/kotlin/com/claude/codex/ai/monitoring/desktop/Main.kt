package com.claude.codex.ai.monitoring.desktop

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.claude.codex.ai.monitoring.desktop.control.ControlCenter
import com.claude.codex.ai.monitoring.desktop.devices.AuditCategory
import com.claude.codex.ai.monitoring.desktop.devices.AuditLog
import com.claude.codex.ai.monitoring.desktop.devices.DeviceStore
import com.claude.codex.ai.monitoring.desktop.hooks.HookInstaller
import com.claude.codex.ai.monitoring.desktop.hooks.HookReceiver
import com.claude.codex.ai.monitoring.desktop.pairing.PairingManager
import com.claude.codex.ai.monitoring.desktop.projects.ProjectStore
import com.claude.codex.ai.monitoring.desktop.session.SessionTracker
import com.claude.codex.ai.monitoring.protocol.ProjectDto
import com.claude.codex.ai.monitoring.desktop.security.DesktopIdentity
import com.claude.codex.ai.monitoring.desktop.security.RateLimiter
import com.claude.codex.ai.monitoring.desktop.server.AgentServer
import com.claude.codex.ai.monitoring.desktop.server.ClientHandler
import com.claude.codex.ai.monitoring.desktop.server.ConnectionHub
import com.claude.codex.ai.monitoring.desktop.session.SessionRegistry
import com.claude.codex.ai.monitoring.desktop.simulator.DemoSessionSimulator
import com.claude.codex.ai.monitoring.desktop.history.HeadlessRunner
import com.claude.codex.ai.monitoring.desktop.history.SessionResumer
import com.claude.codex.ai.monitoring.desktop.history.TranscriptStore
import com.claude.codex.ai.monitoring.desktop.storage.AppStorage
import com.claude.codex.ai.monitoring.desktop.system.AutoStart
import com.claude.codex.ai.monitoring.desktop.system.ComputerOptions
import com.claude.codex.ai.monitoring.desktop.system.DesktopSettings
import com.claude.codex.ai.monitoring.desktop.system.KeepAwake
import com.claude.codex.ai.monitoring.desktop.system.PublicAddress
import com.claude.codex.ai.monitoring.desktop.wrapper.WrapperEndpoint
import com.claude.codex.ai.monitoring.desktop.wrapper.TerminalLauncher
import com.claude.codex.ai.monitoring.desktop.wrapper.WrapperHub
import com.claude.codex.ai.monitoring.desktop.ui.DesktopApp
import com.claude.codex.ai.monitoring.desktop.ui.aggregateState
import com.claude.codex.ai.monitoring.desktop.ui.color
import com.claude.codex.ai.monitoring.desktop.ui.components.StatusDotPainter
import com.claude.codex.ai.monitoring.desktop.ui.theme.DesktopColors
import com.claude.codex.ai.monitoring.desktop.ui.theme.DesktopTheme
import com.claude.codex.ai.monitoring.protocol.ComputerDto
import com.claude.codex.ai.monitoring.protocol.ProtocolCodec
import com.claude.codex.ai.monitoring.protocol.ProtocolConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.nio.file.Path

private const val VERSION = "1.0.8"

/**
 * Arguments: `--background` starts in the tray (used when starting with Windows); `--demo` adds fake sessions for trying
 * the phone without Claude Code; `--public-url=https://…` overrides the tunnel
 * address put in pairing QR codes; `--data-dir=…` moves the key and database (for testing).
 */
fun main(args: Array<String>) {
    fun arg(name: String) = args.firstOrNull { it.startsWith("--$name=") }?.substringAfter("=")
    // Real sessions come from Claude Code hooks (phase 4); demo sessions are opt-in now.
    val demoMode = "--demo" in args
    // Started with Windows: stay in the tray until opened.
    val startHidden = "--background" in args
    // The tunnel address is set on the Overview screen; --public-url or AGENTMON_PUBLIC_URL override it.
    val publicUrlOverride = listOfNotNull(
        arg("public-url")?.let { it to "--public-url" },
        System.getenv("AGENTMON_PUBLIC_URL")?.let { it to "AGENTMON_PUBLIC_URL" },
    ).firstNotNullOfOrNull { (value, source) ->
        (PublicAddress.parse(value) as? PublicAddress.Parsed.Valid)?.let { ComputerOptions.PublicUrlOverride(it.url, source) }
    }
    val computerName = System.getenv("COMPUTERNAME")
        ?: runCatching { InetAddress.getLocalHost().hostName }.getOrNull()
        ?: "This computer"

    val dataDir = AppStorage.dataDirectory(arg("data-dir"))
    val identity = DesktopIdentity.loadOrCreate(dataDir.resolve("desktop-key"))
    val database = AppStorage.openDatabase(dataDir.resolve("agentmon.db"))
    val devices = DeviceStore(database)
    val audit = AuditLog(database)
    val codec = ProtocolCodec()
    val hub = ConnectionHub(codec)
    // The computer id is derived from the desktop key, so it is stable and unique per install.
    val registry = SessionRegistry(ComputerDto(computerId = identity.fingerprint.take(32), name = computerName))
    val pairing = PairingManager(identity, devices, audit, computerName)
    val projects = ProjectStore(database)
    projects.projects.value.forEach { registry.upsertProject(ProjectDto(it.projectId, it.name)) }
    val transcripts = TranscriptStore()
    val tracker = SessionTracker(registry, projects, transcriptTitle = transcripts::titleOf)
    val control = ControlCenter(registry, audit)
    val installer = HookInstaller(HookInstaller.loadOrCreateSecret(dataDir.resolve("hook-secret")))
    // Keep every monitored project's hooks current (new events or timeouts after an update).
    projects.projects.value.forEach { runCatching { installer.install(Path.of(it.path)) } }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Created further down; a wrapper only connects once the server runs.
    lateinit var controller: DesktopController
    val wrappers = WrapperHub(registry, scope, projectFor = { cwd -> controller.monitorForWrapper(cwd) })
    control.wrapper = wrappers
    val headless = HeadlessRunner(registry, scope)
    control.headless = headless
    control.resumer = SessionResumer(
        registry,
        headless,
        transcripts,
        projectPath = { id -> projects.projects.value.firstOrNull { it.projectId == id }?.let { Path.of(it.path) } },
        launcher = TerminalLauncher(dataDir.resolve("launch")),
        projectName = { id -> projects.projects.value.firstOrNull { it.projectId == id }?.name },
    )
    val hookReceiver = HookReceiver(installer.secret, tracker, audit, control, sessionAlias = headless::sessionIdFor)
    val handler = ClientHandler(registry, codec, hub, devices, identity, audit, RateLimiter(), control, wrappers)
    val server = AgentServer(
        handler, pairing, RateLimiter(maxFailures = 10, windowMs = 10 * 60_000L), VERSION, hookReceiver,
        WrapperEndpoint(installer.secret, wrappers, audit),
    )
    val computer = ComputerOptions(DesktopSettings(dataDir.resolve("settings.properties")), KeepAwake(), AutoStart(), scope, publicUrlOverride)
    computer.start(registry.state, control.awayMode)
    controller = DesktopController(
        registry, hub, devices, audit, pairing, identity, demoMode, projects, tracker, installer, control, scope, computer,
    )

    server.start()
    audit.record(AuditCategory.SERVER, "Agent started on ${ProtocolConstants.LOOPBACK_HOST}:${ProtocolConstants.DEFAULT_PORT}")
    scope.launch { tracker.runMaintenance() }
    if (demoMode) scope.launch { DemoSessionSimulator(registry).run() }

    application {
        var windowVisible by remember { mutableStateOf(!startHidden) }
        val state by registry.state.collectAsState()
        val palette = remember { DesktopColors() }
        val trayDot = state.sessions.aggregateState()?.color(palette) ?: palette.stale

        Tray(
            icon = StatusDotPainter(ring = palette.brandStart, dot = trayDot),
            tooltip = "AgentMon",
            onAction = { windowVisible = true },
            menu = {
                Item("Open AgentMon", onClick = { windowVisible = true })
                Item("Pair device", onClick = {
                    windowVisible = true
                    controller.startPairing()
                })
                Separator()
                Item("Quit", onClick = {
                    server.stop()
                    scope.cancel()
                    exitApplication()
                })
            },
        )
        // Closing the window hides it; the agent keeps running in the tray.
        Window(
            onCloseRequest = { windowVisible = false },
            visible = windowVisible,
            title = "AgentMon",
            state = rememberWindowState(width = 1100.dp, height = 740.dp),
        ) {
            DesktopTheme {
                DesktopApp(controller)
            }
        }
    }
}
