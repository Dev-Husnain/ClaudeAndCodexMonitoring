package com.claude.codex.ai.monitoring.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claude.codex.ai.monitoring.desktop.projects.MonitoredProject
import com.claude.codex.ai.monitoring.desktop.ui.components.DesktopCard
import com.claude.codex.ai.monitoring.desktop.ui.components.OutlineButton
import com.claude.codex.ai.monitoring.desktop.ui.components.Pill
import com.claude.codex.ai.monitoring.desktop.ui.components.PrimaryButton
import com.claude.codex.ai.monitoring.desktop.ui.theme.DesktopTheme
import com.claude.codex.ai.monitoring.desktop.ui.toRelative

/** Monitored projects: add (installs hooks), reinstall, remove (uninstalls only our hooks). */
@Composable
fun ProjectsScreen(
    projects: List<MonitoredProject>,
    hooksInstalled: (MonitoredProject) -> Boolean,
    lastHookAt: Map<String, Long>,
    error: String?,
    nowMs: Long,
    onAdd: () -> Unit,
    onReinstall: (MonitoredProject) -> Unit,
    onRemove: (MonitoredProject) -> Unit,
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
                    Text("Projects", style = MaterialTheme.typography.headlineMedium.copy(brush = colors.brandGradient))
                    Text(
                        "Claude Code sessions in these folders show up on your phone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
                PrimaryButton("Add project", onClick = onAdd)
            }
        }
        item {
            Text(
                "Adding a project writes AgentMon hooks into its .claude/settings.local.json (your own hooks are kept, and the " +
                    "file stays out of git). New Claude sessions pick them up; restart a session that is already running.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        if (error != null) {
            item {
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                    modifier = Modifier.fillMaxWidth().background(colors.error.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(12.dp),
                )
            }
        }
        if (projects.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text("No projects yet. Add the folder you run Claude Code in.", style = MaterialTheme.typography.titleLarge, color = colors.textSecondary)
                }
            }
        }
        items(projects, key = { it.projectId }) { project ->
            val installed = hooksInstalled(project)
            DesktopCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(project.name, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                        Text(project.path, style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
                    }
                    Pill(if (installed) "Hooks installed" else "Hooks missing", if (installed) colors.done else colors.waiting)
                }
                Text(
                    lastHookAt[project.projectId]?.let { "Last event ${it.toRelative(nowMs)}" } ?: "No events yet. Start Claude Code in this folder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton(if (installed) "Reinstall hooks" else "Install hooks", onClick = { onReinstall(project) })
                    OutlineButton("Remove", onClick = { onRemove(project) }, color = colors.error)
                }
            }
        }
    }
}
