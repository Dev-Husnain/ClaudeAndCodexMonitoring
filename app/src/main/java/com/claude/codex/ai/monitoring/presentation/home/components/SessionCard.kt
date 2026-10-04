package com.claude.codex.ai.monitoring.presentation.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.theme.MonoTextStyle
import com.claude.codex.ai.monitoring.core.ui.StatusOrb
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.ui.SurfaceCard
import com.claude.codex.ai.monitoring.core.ui.color
import com.claude.codex.ai.monitoring.core.utils.UiText
import com.claude.codex.ai.monitoring.core.utils.resolve
import com.claude.codex.ai.monitoring.presentation.home.SessionItemUiModel

/** One agent session: live orb, project, status, the last message and when it happened. */
@Composable
fun SessionCard(
    session: SessionItemUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onLongClick: () -> Unit = {},
) {
    val statusLabel = session.statusLabel.resolve()
    val cardDescription = stringResource(R.string.cd_session_card, session.projectName, statusLabel)
    SurfaceCard(
        onClick = onClick,
        onLongClick = onLongClick.takeIf { session.removable },
        onLongClickLabel = stringResource(R.string.remove_session_confirm),
        accent = if (highlighted) session.tone.color() else null,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = cardDescription },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            StatusOrb(tone = session.tone)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.projectName,
                    style = MaterialTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                    Text(text = statusLabel, style = MaterialTheme.typography.labelMedium, color = session.tone.color())
                    Text(
                        text = session.relativeTime.resolve(),
                        style = MaterialTheme.typography.labelMedium,
                        color = AppTheme.colors.textSecondary,
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(Dimens.IconMd),
            )
        }
        if (session.snippet != null) {
            Text(
                text = session.snippet,
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textPrimary.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpaceMd),
            )
        }
        if (session.lastTool != null || session.isMonitorOnly) {
            Row(
                modifier = Modifier.padding(top = Dimens.SpaceSm),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
            ) {
                if (session.lastTool != null) {
                    Text(
                        text = session.lastTool.resolve(),
                        style = MonoTextStyle,
                        color = AppTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (session.isMonitorOnly) {
                    Text(
                        text = stringResource(R.string.home_monitor_only),
                        style = MaterialTheme.typography.labelMedium,
                        color = AppTheme.colors.stale,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun SessionCardPreview() {
    AppTheme(darkTheme = true) {
        SessionCard(
            session = SessionItemUiModel(
                sessionId = "s1",
                projectName = "ClaudeMonitoring",
                tone = StatusTone.WAITING,
                statusLabel = UiText.Raw("Needs you"),
                snippet = "Claude needs your permission to use Bash",
                lastTool = UiText.Raw("Last tool: Bash"),
                relativeTime = UiText.Raw("2 min ago"),
                isMonitorOnly = true,
            ),
            onClick = {},
            highlighted = true,
        )
    }
}
