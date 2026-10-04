package com.claude.codex.ai.monitoring.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claude.codex.ai.monitoring.desktop.session.RegistryState
import com.claude.codex.ai.monitoring.desktop.ui.components.DesktopCard
import com.claude.codex.ai.monitoring.desktop.ui.components.Pill
import com.claude.codex.ai.monitoring.desktop.ui.components.SessionTile
import com.claude.codex.ai.monitoring.desktop.ui.components.StatusDot
import com.claude.codex.ai.monitoring.desktop.ui.components.SwitchRow
import com.claude.codex.ai.monitoring.desktop.ui.theme.DesktopTheme
import com.claude.codex.ai.monitoring.protocol.ProtocolConstants

/** What the "This computer" card shows. */
data class ComputerOptionsUiModel(
    val keepAwake: Boolean,
    val startWithWindows: Boolean,
    val autoStartSupported: Boolean,
    val error: String?,
)

/** Agent status, this computer's options, and live session cards grouped by project. */
@Composable
fun OverviewScreen(
    registry: RegistryState,
    connectedPhones: Int,
    demoMode: Boolean,
    computer: ComputerOptionsUiModel,
    onKeepAwakeChange: (Boolean) -> Unit,
    onStartWithWindowsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DesktopTheme.colors
    val projectNames = registry.projects.associate { it.projectId to it.name }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(28.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Overview", style = MaterialTheme.typography.headlineMedium.copy(brush = colors.brandGradient))
                Text(
                    "Live Claude Code sessions on this computer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            DesktopCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusDot(color = colors.done, pulsing = true)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Listening on ${ProtocolConstants.LOOPBACK_HOST}:${ProtocolConstants.DEFAULT_PORT}",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Text(
                            "Loopback only. Phones reach it through adb reverse or the Cloudflare tunnel.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                    Pill(text = "$connectedPhones phone${if (connectedPhones == 1) "" else "s"} connected", color = colors.running)
                    if (demoMode) Pill(text = "Demo sessions", color = colors.waiting)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            DesktopCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("This computer", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    SwitchRow(
                        title = "Stay awake while Claude works",
                        summary = "Keeps Windows from sleeping while a session runs or waits for you, or Away mode is on. " +
                            "The screen can still turn off; closing a laptop lid still sleeps.",
                        checked = computer.keepAwake,
                        onChange = onKeepAwakeChange,
                    )
                    if (computer.autoStartSupported) {
                        SwitchRow(
                            title = "Start with Windows",
                            summary = "Starts AgentMon in the tray when you sign in, so your phone can reach Claude after a restart.",
                            checked = computer.startWithWindows,
                            onChange = onStartWithWindowsChange,
                        )
                    }
                    computer.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error) }
                }
            }
        }
        if (registry.sessions.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text("No sessions yet", style = MaterialTheme.typography.titleLarge, color = colors.textSecondary)
                }
            }
        }
        registry.sessions
            .groupBy { it.projectId }
            .forEach { (projectId, sessions) ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "header-$projectId") {
                    Text(
                        text = (projectNames[projectId] ?: projectId).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(sessions, key = { it.sessionId }) { session ->
                    SessionTile(session = session, projectName = projectNames[projectId] ?: projectId)
                }
            }
    }
}
