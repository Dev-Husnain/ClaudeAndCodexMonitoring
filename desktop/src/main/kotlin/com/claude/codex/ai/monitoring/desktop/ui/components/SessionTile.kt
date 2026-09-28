package com.claude.codex.ai.monitoring.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.claude.codex.ai.monitoring.desktop.ui.color
import com.claude.codex.ai.monitoring.desktop.ui.label
import com.claude.codex.ai.monitoring.desktop.ui.theme.DesktopTheme
import com.claude.codex.ai.monitoring.protocol.ControlMode
import com.claude.codex.ai.monitoring.protocol.SessionDto
import com.claude.codex.ai.monitoring.protocol.SessionState

/** Live session card on the desktop overview. */
@Composable
fun SessionTile(
    session: SessionDto,
    projectName: String,
    modifier: Modifier = Modifier,
) {
    val colors = DesktopTheme.colors
    val stateColor = session.state.color()
    DesktopCard(modifier = modifier, accent = if (session.state == SessionState.WAITING_INPUT) stateColor else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusDot(color = stateColor, pulsing = session.state == SessionState.RUNNING || session.state == SessionState.WAITING_INPUT)
            Column(modifier = Modifier.weight(1f)) {
                Text(projectName, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(session.state.label(), style = MaterialTheme.typography.labelMedium, color = stateColor)
            }
            Text(
                text = if (session.controlMode == ControlMode.MONITOR_ONLY) "Monitor only" else "Controllable",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
        session.lastMessageSnippet?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        session.lastTool?.let {
            Text(
                text = "Last tool: $it",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Text(
            text = session.sessionId.take(8),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
