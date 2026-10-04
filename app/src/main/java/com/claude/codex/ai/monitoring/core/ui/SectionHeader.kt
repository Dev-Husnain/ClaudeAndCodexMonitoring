package com.claude.codex.ai.monitoring.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.theme.PillShape

/** Uppercase section label with an optional count badge tinted by [tone]. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    tone: StatusTone? = null,
    /** An optional text action at the end of the row (e.g. "History"). */
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val accent = tone?.color() ?: AppTheme.colors.textSecondary
    Row(
        modifier = if (actionLabel != null) modifier.fillMaxWidth() else modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
        )
        if (count != null) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                modifier = Modifier
                    .background(accent.copy(alpha = 0.14f), PillShape)
                    .padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceXxs),
            )
        }
        if (actionLabel != null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = AppTheme.colors.brandStart,
                modifier = Modifier
                    .clip(PillShape)
                    .clickable(role = Role.Button, onClick = onAction)
                    .heightIn(min = Dimens.TouchTarget)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .padding(horizontal = Dimens.SpaceMd),
            )
        }
    }
}

@Preview
@Composable
private fun SectionHeaderPreview() {
    AppTheme(darkTheme = true) {
        SectionHeader(title = "Needs you", count = 2, tone = StatusTone.WAITING)
    }
}
