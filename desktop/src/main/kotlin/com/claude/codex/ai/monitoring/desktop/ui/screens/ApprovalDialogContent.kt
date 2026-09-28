package com.claude.codex.ai.monitoring.desktop.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claude.codex.ai.monitoring.desktop.devices.DeviceGrant
import com.claude.codex.ai.monitoring.desktop.pairing.PendingApproval
import com.claude.codex.ai.monitoring.desktop.ui.components.DesktopCard
import com.claude.codex.ai.monitoring.desktop.ui.components.OutlineButton
import com.claude.codex.ai.monitoring.desktop.ui.components.PrimaryButton
import com.claude.codex.ai.monitoring.desktop.ui.components.SwitchRow
import com.claude.codex.ai.monitoring.desktop.ui.components.ToggleChip
import com.claude.codex.ai.monitoring.desktop.ui.theme.DesktopTheme
import com.claude.codex.ai.monitoring.protocol.AgentCrypto
import com.claude.codex.ai.monitoring.protocol.ProjectDto

/**
 * "Approve <phone>?" (spec 6.2 step 3). Least privilege by default: read-only. The owner picks
 * which projects the phone may see. Nothing is stored unless Approve is pressed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApprovalDialogContent(
    pending: PendingApproval,
    projects: List<ProjectDto>,
    onApprove: (DeviceGrant) -> Unit,
    onReject: () -> Unit,
) {
    val colors = DesktopTheme.colors
    var canSendInput by remember(pending.requestId) { mutableStateOf(false) }
    var allProjects by remember(pending.requestId) { mutableStateOf(true) }
    var selected by remember(pending.requestId) { mutableStateOf(emptySet<String>()) }
    val valid = allProjects || selected.isNotEmpty()

    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).padding(28.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Approve “${pending.deviceName}”?", style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary)
        Text(
            "Check that the phone shows the same key before approving:",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Text(
            AgentCrypto.shortFingerprint(pending.fingerprint),
            style = MaterialTheme.typography.titleLarge.copy(brush = colors.brandGradient),
        )
        Text("Request from ${pending.remote}", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)

        DesktopCard(modifier = Modifier.fillMaxWidth()) {
            SwitchRow(
                title = "Allow sending input",
                summary = if (canSendInput) "Can type instructions and approve or deny prompts" else "Read-only: can watch sessions but not control them",
                checked = canSendInput,
                onChange = { canSendInput = it },
            )
        }
        DesktopCard(modifier = Modifier.fillMaxWidth()) {
            SwitchRow(
                title = "All projects",
                summary = "Includes projects added later",
                checked = allProjects,
                onChange = { allProjects = it },
            )
            if (!allProjects) {
                Text(
                    if (projects.isEmpty()) "No projects yet. Keep \"All projects\" on, or add a project first." else "Only these projects:",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    projects.forEach { project ->
                        val isOn = project.projectId in selected
                        ToggleChip(project.name, isOn, { selected = if (isOn) selected - project.projectId else selected + project.projectId })
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlineButton("Reject", onClick = onReject, color = colors.error)
            PrimaryButton(
                "Approve",
                enabled = valid,
                onClick = { onApprove(DeviceGrant(canSendInput, allProjects, if (allProjects) emptySet() else selected)) },
            )
        }
    }
}
