package com.claude.codex.ai.monitoring.presentation.sessiondetail.components

import androidx.compose.foundation.layout.Arrangement
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
import com.claude.codex.ai.monitoring.core.ui.GradientButton
import com.claude.codex.ai.monitoring.core.ui.QuickActionChip
import com.claude.codex.ai.monitoring.core.ui.StatusOrb
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.ui.SurfaceCard
import com.claude.codex.ai.monitoring.presentation.sessiondetail.AwaitingUiModel

/** What Claude is waiting for, with the matching one-tap answers. */
@Composable
fun AwaitingCard(
    awaiting: AwaitingUiModel,
    enabled: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onStop: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    SurfaceCard(modifier = modifier.fillMaxWidth(), accent = colors.waiting) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            StatusOrb(tone = StatusTone.WAITING)
            Text(
                text = stringResource(if (awaiting.isPermission) R.string.detail_awaiting_permission_title else R.string.detail_awaiting_reply_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }
        if (awaiting.detail != null) {
            Text(
                text = awaiting.detail,
                style = if (awaiting.isPermission) MonoTextStyle else MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                maxLines = if (awaiting.isPermission) 4 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Dimens.SpaceMd),
            )
        }
        if (awaiting.isPermission) {
            GradientButton(
                text = stringResource(R.string.action_approve),
                onClick = onApprove,
                enabled = enabled,
                leadingIcon = R.drawable.ic_check,
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpaceLg),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpaceSm),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                QuickActionChip(stringResource(R.string.action_deny), onDeny, tone = StatusTone.ERROR, enabled = enabled, modifier = Modifier.weight(1f))
                QuickActionChip(stringResource(R.string.action_stop), onStop, tone = StatusTone.STALE, enabled = enabled, modifier = Modifier.weight(1f))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpaceLg),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                QuickActionChip(stringResource(R.string.action_continue), onContinue, tone = StatusTone.RUNNING, enabled = enabled, modifier = Modifier.weight(1f))
                QuickActionChip(stringResource(R.string.action_let_it_stop), onStop, tone = StatusTone.STALE, enabled = enabled, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Preview
@Composable
private fun AwaitingCardPreview() {
    AppTheme(darkTheme = true) {
        AwaitingCard(
            awaiting = AwaitingUiModel(isPermission = true, detail = "Bash: ./gradlew connectedAndroidTest"),
            enabled = true,
            onApprove = {},
            onDeny = {},
            onStop = {},
            onContinue = {},
        )
    }
}
