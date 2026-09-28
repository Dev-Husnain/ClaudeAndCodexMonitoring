package com.claude.codex.ai.monitoring.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.claude.codex.ai.monitoring.desktop.server.ConnectionTracker
import com.claude.codex.ai.monitoring.desktop.session.SessionRegistry
import com.claude.codex.ai.monitoring.desktop.ui.components.StatusDot
import com.claude.codex.ai.monitoring.desktop.ui.screens.OverviewScreen
import com.claude.codex.ai.monitoring.desktop.ui.theme.DesktopTheme

/** Main window: sidebar navigation and the overview. Devices, Pair and Activity arrive in later phases. */
@Composable
fun DesktopApp(
    registry: SessionRegistry,
    connections: ConnectionTracker,
    demoMode: Boolean,
) {
    val state by registry.state.collectAsState()
    val phones by connections.connected.collectAsState()
    val colors = DesktopTheme.colors
    Row(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(colors.surface)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusDot(color = state.sessions.aggregateState()?.color() ?: colors.stale, pulsing = true)
                Text("AgentMon", style = MaterialTheme.typography.titleLarge.copy(brush = colors.brandGradient))
            }
            Text(registry.computer.name, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            Box(
                modifier = Modifier
                    .padding(top = 20.dp)
                    .fillMaxWidth()
                    .background(colors.surfaceElevated, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text("Overview", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            }
        }
        OverviewScreen(registry = state, connectedPhones = phones.values.sum(), demoMode = demoMode)
    }
}
