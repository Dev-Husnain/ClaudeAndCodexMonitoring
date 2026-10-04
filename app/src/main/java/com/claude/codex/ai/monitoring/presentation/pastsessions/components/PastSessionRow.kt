package com.claude.codex.ai.monitoring.presentation.pastsessions.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.ui.StatusPill
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.ui.SurfaceCard
import com.claude.codex.ai.monitoring.core.utils.UiText
import com.claude.codex.ai.monitoring.core.utils.resolve
import com.claude.codex.ai.monitoring.presentation.pastsessions.PastSessionItemUiModel

/** One saved conversation: its title, when it was last active, and whether Claude still has it open. */
@Composable
fun PastSessionRow(
    session: PastSessionItemUiModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SurfaceCard(
        onClick = onClick,
        onLongClick = onLongClick.takeIf { session.openSessionId == null },
        onLongClickLabel = stringResource(R.string.remove_session_confirm),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = session.relativeTime.resolve(),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                )
            }
            if (session.openSessionId != null) {
                StatusPill(tone = StatusTone.RUNNING, label = stringResource(R.string.history_open_now), animateOrb = false)
            }
        }
    }
}

@Preview
@Composable
private fun PastSessionRowPreview() {
    AppTheme(darkTheme = true) {
        PastSessionRow(
            session = PastSessionItemUiModel("c1", "Fix the login bug and add tests", UiText.Raw("2 h ago"), 0L, openSessionId = null),
            onClick = {},
            onLongClick = {},
        )
    }
}
