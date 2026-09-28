package com.claude.codex.ai.monitoring.presentation.home.components

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
import com.claude.codex.ai.monitoring.core.ui.IconActionButton
import com.claude.codex.ai.monitoring.core.ui.StatusPill
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.utils.UiText
import com.claude.codex.ai.monitoring.core.utils.resolve
import com.claude.codex.ai.monitoring.presentation.common.ConnectionUiModel

/** Gradient wordmark, the computer being watched, the live connection pill and a settings button. */
@Composable
fun HomeHeader(
    computerName: String?,
    connection: ConnectionUiModel,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium.copy(brush = AppTheme.colors.brandGradient),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                StatusPill(tone = connection.tone, label = connection.label.resolve())
                if (readOnly) {
                    StatusPill(tone = StatusTone.STALE, label = stringResource(R.string.home_read_only), animateOrb = false)
                }
                Text(
                    text = computerName?.let { stringResource(R.string.home_subtitle_computer, it) }
                        ?: stringResource(R.string.home_subtitle_waiting),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconActionButton(
            icon = R.drawable.ic_settings,
            contentDescription = stringResource(R.string.action_settings),
            onClick = onSettingsClick,
        )
    }
}

@Preview
@Composable
private fun HomeHeaderPreview() {
    AppTheme(darkTheme = true) {
        HomeHeader(
            computerName = "Workstation",
            connection = ConnectionUiModel(StatusTone.DONE, UiText.Raw("Connected")),
            onSettingsClick = {},
        )
    }
}
