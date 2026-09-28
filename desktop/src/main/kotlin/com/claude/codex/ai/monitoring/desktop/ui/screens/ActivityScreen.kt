package com.claude.codex.ai.monitoring.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.claude.codex.ai.monitoring.desktop.devices.AuditCategory
import com.claude.codex.ai.monitoring.desktop.devices.AuditEntry
import com.claude.codex.ai.monitoring.desktop.ui.components.DesktopCard
import com.claude.codex.ai.monitoring.desktop.ui.components.Pill
import com.claude.codex.ai.monitoring.desktop.ui.components.ToggleChip
import com.claude.codex.ai.monitoring.desktop.ui.theme.DesktopTheme
import com.claude.codex.ai.monitoring.desktop.ui.toClockTime

/** Security audit trail with category filters (spec 9.4). */
@Composable
fun ActivityScreen(
    entries: List<AuditEntry>,
    deviceNames: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val colors = DesktopTheme.colors
    var filter by remember { mutableStateOf<AuditCategory?>(null) }
    val shown = entries.filter { filter == null || it.category == filter }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Activity", style = MaterialTheme.typography.headlineMedium.copy(brush = colors.brandGradient))
                Text(
                    "Pairing, sign-ins and access changes. Session content is never recorded here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                ToggleChip("All", filter == null, { filter = null })
                AuditCategory.entries.forEach { category ->
                    ToggleChip(category.label(), filter == category, { filter = category })
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text("Nothing recorded yet", style = MaterialTheme.typography.titleLarge, color = colors.textSecondary)
                }
            }
        }
        items(shown, key = { it.id }) { entry ->
            DesktopCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        entry.timestampMs.toClockTime(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary,
                        modifier = Modifier.width(150.dp),
                    )
                    Pill(entry.category.label(), entry.category.color())
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.message, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                        val source = listOfNotNull(entry.deviceId?.let { deviceNames[it] ?: it.take(8) }, entry.remote).joinToString("  ·  ")
                        if (source.isNotEmpty()) {
                            Text(source, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

private fun AuditCategory.label(): String = when (this) {
    AuditCategory.PAIRING -> "Pairing"
    AuditCategory.AUTH -> "Sign-in"
    AuditCategory.ACCESS -> "Access"
    AuditCategory.SERVER -> "Server"
}

@Composable
private fun AuditCategory.color() = when (this) {
    AuditCategory.PAIRING -> DesktopTheme.colors.brandEnd
    AuditCategory.AUTH -> DesktopTheme.colors.waiting
    AuditCategory.ACCESS -> DesktopTheme.colors.brandStart
    AuditCategory.SERVER -> DesktopTheme.colors.stale
}
