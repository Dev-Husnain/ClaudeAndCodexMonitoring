package com.claude.codex.ai.monitoring.desktop.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claude.codex.ai.monitoring.desktop.devices.DeviceGrant
import com.claude.codex.ai.monitoring.desktop.devices.PairedDevice
import com.claude.codex.ai.monitoring.desktop.ui.components.DesktopCard
import com.claude.codex.ai.monitoring.desktop.ui.components.OutlineButton
import com.claude.codex.ai.monitoring.desktop.ui.components.Pill
import com.claude.codex.ai.monitoring.desktop.ui.components.PrimaryButton
import com.claude.codex.ai.monitoring.desktop.ui.components.StatusDot
import com.claude.codex.ai.monitoring.desktop.ui.components.SwitchRow
import com.claude.codex.ai.monitoring.desktop.ui.components.ToggleChip
import com.claude.codex.ai.monitoring.desktop.ui.theme.DesktopTheme
import com.claude.codex.ai.monitoring.desktop.ui.toRelative
import com.claude.codex.ai.monitoring.protocol.AgentCrypto
import com.claude.codex.ai.monitoring.protocol.ProjectDto

/** Paired phones with live permission toggles and instant revoke (spec 6.4, 9.4). */
@Composable
fun DevicesScreen(
    devices: List<PairedDevice>,
    online: Map<String, Int>,
    projects: List<ProjectDto>,
    nowMs: Long,
    onGrantChange: (PairedDevice, DeviceGrant) -> Unit,
    onRevoke: (PairedDevice) -> Unit,
    onPair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DesktopTheme.colors
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Devices", style = MaterialTheme.typography.headlineMedium.copy(brush = colors.brandGradient))
                    Text("Phones that can see this computer", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
                PrimaryButton("Pair device", onClick = onPair)
            }
        }
        if (devices.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text("No phones paired yet", style = MaterialTheme.typography.titleLarge, color = colors.textSecondary)
                }
            }
        }
        items(devices, key = { it.deviceId }) { device ->
            DeviceCard(
                device = device,
                isOnline = (online[device.deviceId] ?: 0) > 0,
                projects = projects,
                nowMs = nowMs,
                onGrantChange = { onGrantChange(device, it) },
                onRevoke = { onRevoke(device) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceCard(
    device: PairedDevice,
    isOnline: Boolean,
    projects: List<ProjectDto>,
    nowMs: Long,
    onGrantChange: (DeviceGrant) -> Unit,
    onRevoke: () -> Unit,
) {
    val colors = DesktopTheme.colors
    var confirmRevoke by remember(device.deviceId) { mutableStateOf(false) }
    val grant = device.grant
    DesktopCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusDot(color = if (isOnline) colors.done else colors.stale, pulsing = isOnline)
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                Text(
                    buildString {
                        append(if (isOnline) "Online" else device.lastSeenAtMs?.let { "Last seen ${it.toRelative(nowMs)}" } ?: "Never connected")
                        append("  ·  paired ${device.pairedAtMs.toRelative(nowMs)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            Pill(if (grant.canSendInput) "Can send input" else "Read-only", if (grant.canSendInput) colors.brandEnd else colors.stale)
        }
        Text(
            "Key  ${AgentCrypto.shortFingerprint(device.fingerprint)}",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        SwitchRow(
            title = "Allow sending input",
            summary = "Type instructions and approve or deny prompts",
            checked = grant.canSendInput,
            onChange = { onGrantChange(grant.copy(canSendInput = it)) },
        )
        SwitchRow(
            title = "All projects",
            summary = "Includes projects added later",
            checked = grant.allProjects,
            onChange = { onGrantChange(grant.copy(allProjects = it)) },
            modifier = Modifier.padding(top = 8.dp),
        )
        if (!grant.allProjects && projects.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                projects.forEach { project ->
                    val allowed = project.projectId in grant.projectIds
                    ToggleChip(project.name, allowed, {
                        val ids = if (allowed) grant.projectIds - project.projectId else grant.projectIds + project.projectId
                        onGrantChange(grant.copy(projectIds = ids))
                    })
                }
            }
        }
        Row(
            modifier = Modifier.padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (confirmRevoke) {
                Text("Revoke and disconnect now?", style = MaterialTheme.typography.bodyMedium, color = colors.error)
                OutlineButton("Revoke", onClick = onRevoke, color = colors.error)
                OutlineButton("Cancel", onClick = { confirmRevoke = false }, color = colors.textSecondary)
            } else {
                OutlineButton("Revoke access", onClick = { confirmRevoke = true }, color = colors.error)
            }
        }
    }
}
