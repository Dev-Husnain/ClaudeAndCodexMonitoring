package com.claude.codex.ai.monitoring.presentation.sessiondetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.claude.codex.ai.monitoring.core.theme.MonoTextStyle
import com.claude.codex.ai.monitoring.core.ui.StatusOrb
import com.claude.codex.ai.monitoring.core.ui.StatusPill
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.ui.SurfaceCard
import com.claude.codex.ai.monitoring.core.ui.color
import com.claude.codex.ai.monitoring.core.utils.UiText
import com.claude.codex.ai.monitoring.core.utils.resolve
import com.claude.codex.ai.monitoring.presentation.sessiondetail.SessionHeaderUiModel

/** Big live status card at the top of the session detail. */
@Composable
fun SessionStatusHeader(
    header: SessionHeaderUiModel,
    modifier: Modifier = Modifier,
) {
    SurfaceCard(
        accent = header.tone.color(),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        ) {
            StatusOrb(tone = header.tone, size = Dimens.OrbLg)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = header.statusLabel.resolve(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = header.tone.color(),
                )
                Text(
                    text = header.started.resolve(),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                )
            }
            StatusPill(tone = header.controlTone, label = header.controlLabel.resolve(), animateOrb = false)
        }
        if (header.message != null) {
            Text(
                text = header.message,
                style = MaterialTheme.typography.bodyLarge,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.padding(top = Dimens.SpaceLg),
            )
        }
        if (header.errorInfo != null) {
            Text(
                text = stringResource(R.string.detail_error_info, header.errorInfo),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.error,
                modifier = Modifier.padding(top = Dimens.SpaceSm),
            )
        }
        if (header.lastTool != null) {
            Row(
                modifier = Modifier
                    .padding(top = Dimens.SpaceMd)
                    .background(AppTheme.colors.surfaceElevated, MaterialTheme.shapes.small)
                    .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceSm)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                Text(
                    text = stringResource(R.string.detail_last_tool),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppTheme.colors.textSecondary,
                )
                Text(
                    text = header.lastTool,
                    style = MonoTextStyle,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview
@Composable
private fun SessionStatusHeaderPreview() {
    AppTheme(darkTheme = true) {
        SessionStatusHeader(
            header = SessionHeaderUiModel(
                tone = StatusTone.WAITING,
                statusLabel = UiText.Raw("Needs you"),
                started = UiText.Raw("Started 12 min ago"),
                lastTool = "Bash",
                controlLabel = UiText.Raw("Monitor only"),
                controlTone = StatusTone.STALE,
                message = "Claude needs your permission to use Bash",
                errorInfo = null,
            ),
        )
    }
}
