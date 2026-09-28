package com.claude.codex.ai.monitoring.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.claude.codex.ai.monitoring.desktop.server.AgentServer
import com.claude.codex.ai.monitoring.desktop.server.ClientHandler
import com.claude.codex.ai.monitoring.desktop.server.ConnectionTracker
import com.claude.codex.ai.monitoring.desktop.session.SessionRegistry
import com.claude.codex.ai.monitoring.desktop.simulator.DemoSessionSimulator
import com.claude.codex.ai.monitoring.desktop.ui.DesktopApp
import com.claude.codex.ai.monitoring.desktop.ui.aggregateState
import com.claude.codex.ai.monitoring.desktop.ui.color
import com.claude.codex.ai.monitoring.desktop.ui.components.StatusDotPainter
import com.claude.codex.ai.monitoring.desktop.ui.theme.DesktopColors
import com.claude.codex.ai.monitoring.desktop.ui.theme.DesktopTheme
import com.claude.codex.ai.monitoring.protocol.ComputerDto
import com.claude.codex.ai.monitoring.protocol.ProtocolCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.util.UUID

private const val VERSION = "1.0.0"

fun main(args: Array<String>) {
    // Phase 1 runs demo sessions by default; pass --no-demo once real hooks exist (phase 4).
    val demoMode = "--no-demo" !in args
    val computerName = System.getenv("COMPUTERNAME")
        ?: runCatching { InetAddress.getLocalHost().hostName }.getOrNull()
        ?: "This computer"
    val registry = SessionRegistry(ComputerDto(computerId = UUID.nameUUIDFromBytes(computerName.toByteArray()).toString(), name = computerName))
    val connections = ConnectionTracker()
    val server = AgentServer(ClientHandler(registry, ProtocolCodec(), connections), VERSION)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    server.start()
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
            state = rememberWindowState(width = 1100.dp, height = 720.dp),
        ) {
            DesktopTheme {
                DesktopApp(registry = registry, connections = connections, demoMode = demoMode)
            }
        }
    }
}
