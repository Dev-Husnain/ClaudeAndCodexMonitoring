package com.claude.codex.ai.monitoring.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.claude.codex.ai.monitoring.desktop.DesktopController
import com.claude.codex.ai.monitoring.desktop.ui.components.PrimaryButton
import com.claude.codex.ai.monitoring.desktop.ui.components.StatusDot
import com.claude.codex.ai.monitoring.desktop.ui.components.SwitchRow
import com.claude.codex.ai.monitoring.desktop.ui.screens.ActivityScreen
import com.claude.codex.ai.monitoring.desktop.ui.screens.ApprovalDialogContent
import com.claude.codex.ai.monitoring.desktop.ui.screens.DevicesScreen
import com.claude.codex.ai.monitoring.desktop.ui.screens.ComputerOptionsUiModel
import com.claude.codex.ai.monitoring.desktop.ui.screens.OverviewScreen
import com.claude.codex.ai.monitoring.desktop.ui.screens.PairDialogContent
import com.claude.codex.ai.monitoring.desktop.ui.screens.ProjectsScreen
import java.nio.file.Path
import javax.swing.JFileChooser
import com.claude.codex.ai.monitoring.desktop.ui.theme.DesktopTheme
import kotlinx.coroutines.delay

private enum class Tab(val label: String) { OVERVIEW("Overview"), PROJECTS("Projects"), DEVICES("Devices"), ACTIVITY("Activity") }

/** Main window: sidebar navigation, the three screens, and the pair and approve dialogs. */
@Composable
fun DesktopApp(controller: DesktopController) {
    val registryState by controller.registry.state.collectAsState()
    val online by controller.hub.online.collectAsState()
    val devices by controller.devices.devices.collectAsState()
    val audit by controller.audit.entries.collectAsState()
    val offer by controller.pairing.offer.collectAsState()
    val pending by controller.pairing.pending.collectAsState()
    val projects by controller.projects.projects.collectAsState()
    val lastHookAt by controller.tracker.lastHookAt.collectAsState()
    val projectError by controller.projectError.collectAsState()
    val awayMode by controller.control.awayMode.collectAsState()
    val keepAwake by controller.computer.keepAwakeEnabled.collectAsState()
    val startWithWindows by controller.computer.startWithWindows.collectAsState()
    val computerError by controller.computer.error.collectAsState()
    val publicUrl by controller.computer.publicUrl.collectAsState()
    val addressError by controller.computer.addressError.collectAsState()
    val colors = DesktopTheme.colors

    var tab by remember { mutableStateOf(Tab.OVERVIEW) }
    val route by controller.route.collectAsState()
    val tunnelReachable by controller.tunnelReachable.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    Row(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier.width(220.dp).fillMaxHeight().background(colors.surface).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusDot(color = registryState.sessions.aggregateState()?.color() ?: colors.stale, pulsing = true)
                Text("AgentMon", style = MaterialTheme.typography.titleLarge.copy(brush = colors.brandGradient))
            }
            Text(controller.registry.computer.name, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            Spacer(Modifier.padding(top = 14.dp))
            Tab.entries.forEach { item ->
                val selected = item == tab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) colors.surfaceElevated else colors.surface)
                        .clickable { tab = item }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) colors.textPrimary else colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    val count = when (item) {
                        Tab.DEVICES -> devices.size
                        Tab.PROJECTS -> projects.size
                        else -> 0
                    }
                    if (count > 0) {
                        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            SwitchRow(
                title = "Away mode",
                summary = if (awayMode) "Claude waits for your phone" else "Off: Claude works as usual",
                checked = awayMode,
                onChange = controller::setAwayMode,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            PrimaryButton("Pair device", onClick = { controller.startPairing() }, modifier = Modifier.fillMaxWidth())
        }
        when (tab) {
            Tab.OVERVIEW -> OverviewScreen(
                registry = registryState,
                connectedPhones = online.size,
                demoMode = controller.demoMode,
                computer = ComputerOptionsUiModel(
                    keepAwake = keepAwake,
                    startWithWindows = startWithWindows,
                    autoStartSupported = controller.computer.autoStartSupported,
                    error = computerError,
                    publicAddress = publicUrl,
                    publicAddressLockedBy = controller.computer.publicUrlLockedBy,
                    publicAddressError = addressError,
                ),
                onPublicAddressSave = { controller.computer.setPublicUrl(it) },
                onKeepAwakeChange = controller.computer::setKeepAwake,
                onStartWithWindowsChange = controller.computer::setStartWithWindows,
            )
            Tab.PROJECTS -> ProjectsScreen(
                projects = projects,
                hooksInstalled = controller::hooksInstalled,
                lastHookAt = lastHookAt,
                error = projectError,
                nowMs = now,
                onAdd = { chooseFolder()?.let(controller::addProject) },
                onReinstall = controller::reinstallHooks,
                onRemove = controller::removeProject,
            )
            Tab.DEVICES -> DevicesScreen(
                devices = devices,
                online = online,
                projects = registryState.projects,
                nowMs = now,
                onGrantChange = controller::updateGrant,
                onRevoke = controller::revoke,
                onPair = { controller.startPairing() },
            )
            Tab.ACTIVITY -> ActivityScreen(
                entries = audit,
                deviceNames = devices.associate { it.deviceId to it.name },
                onClear = controller.audit::clear,
            )
        }
    }

    offer?.let { current ->
        DialogWindow(
            onCloseRequest = controller::cancelPairing,
            title = "Pair a phone",
            state = rememberDialogState(width = 520.dp, height = 820.dp),
        ) {
            DesktopTheme {
                PairDialogContent(
                    offer = current,
                    route = route,
                    tunnelHost = publicUrl?.substringAfter("://"),
                    tunnelReachable = tunnelReachable,
                    desktopFingerprint = controller.identity.fingerprint,
                    onRouteChange = { controller.startPairing(it) },
                    onRegenerate = { controller.startPairing(route) },
                    onClose = controller::cancelPairing,
                )
            }
        }
    }

    pending?.let { request ->
        DialogWindow(
            onCloseRequest = controller::reject,
            title = "Approve device",
            state = rememberDialogState(width = 520.dp, height = 640.dp),
            alwaysOnTop = true,
        ) {
            DesktopTheme {
                ApprovalDialogContent(
                    pending = request,
                    projects = registryState.projects,
                    onApprove = controller::approve,
                    onReject = controller::reject,
                )
            }
        }
    }
}

/** Native folder picker for "Add project"; null when cancelled. */
private fun chooseFolder(): Path? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Choose the project folder you run Claude Code in"
        isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
}
