package com.claude.codex.ai.monitoring.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens

/**
 * Away mode switch. When on, the computer holds Claude at permission prompts and turn ends until
 * the phone answers; the card glows so it is obvious Claude may be waiting.
 */
@Composable
fun AwayModeToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    val colors = AppTheme.colors
    SurfaceCard(
        modifier = modifier.fillMaxWidth(),
        accent = if (enabled) colors.waiting else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = enabled, enabled = !busy, role = Role.Switch, onValueChange = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        ) {
            StatusOrb(tone = if (enabled) StatusTone.WAITING else StatusTone.STALE, size = Dimens.OrbSm, animated = enabled)
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.away_mode_title), style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                Text(
                    stringResource(if (enabled) R.string.away_mode_on else R.string.away_mode_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = null,
                enabled = !busy,
                modifier = Modifier.size(Dimens.TouchTarget),
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.waiting,
                    checkedThumbColor = colors.onBrand,
                    uncheckedTrackColor = colors.surfaceElevated,
                    uncheckedBorderColor = colors.outline,
                    uncheckedThumbColor = colors.textSecondary,
                ),
            )
        }
    }
}

@Preview
@Composable
private fun AwayModeTogglePreview() {
    AppTheme(darkTheme = true) {
        AwayModeToggle(enabled = true, onToggle = {})
    }
}
