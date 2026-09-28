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
import com.claude.codex.ai.monitoring.desktop.devices.AuditCategory
import com.claude.codex.ai.monitoring.desktop.devices.AuditLog
import com.claude.codex.ai.monitoring.desktop.devices.DeviceStore
import com.claude.codex.ai.monitoring.desktop.pairing.PairingManager
import com.claude.codex.ai.monitoring.desktop.security.DesktopIdentity
import com.claude.codex.ai.monitoring.desktop.security.RateLimiter
import com.claude.codex.ai.monitoring.desktop.server.AgentServer
import com.claude.codex.ai.monitoring.desktop.server.ClientHandler
import com.claude.codex.ai.monitoring.desktop.server.ConnectionHub
import com.claude.codex.ai.monitoring.desktop.session.SessionRegistry
import com.claude.codex.ai.monitoring.desktop.simulator.DemoSessionSimulator
import com.claude.codex.ai.monitoring.desktop.storage.AppStorage
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

private const val VERSION = "1.0.0"

/**
 * Arguments: `--no-demo` disables demo sessions; `--public-url=https://…` overrides the tunnel
 * address put in pairing QR codes; `--data-dir=…` moves the key and database (for testing).
 */
fun main(args: Array<String>) {
    fun arg(name: String) = args.firstOrNull { it.startsWith("--$name=") }?.substringAfter("=")
    val demoMode = "--no-demo" !in args
    val publicUrl = arg("public-url") ?: "https://${ProtocolConstants.PUBLIC_HOST}"
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
    val handler = ClientHandler(registry, codec, hub, devices, identity, audit, RateLimiter())
    val server = AgentServer(handler, pairing, RateLimiter(maxFailures = 10, windowMs = 10 * 60_000L), VERSION)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val controller = DesktopController(registry, hub, devices, audit, pairing, identity, publicUrl, demoMode, scope)

    server.start()
    audit.record(AuditCategory.SERVER, "Agent started on ${ProtocolConstants.LOOPBACK_HOST}:${ProtocolConstants.DEFAULT_PORT}")
    if (demoMode) scope.launch { DemoSessionSimulator(registry).run() }

    application {
        var windowVisible by remember { mutableStateOf(true) }
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
