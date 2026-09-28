package com.claude.codex.ai.monitoring.presentation.sessiondetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.theme.MonoTextStyle
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.ui.color
import com.claude.codex.ai.monitoring.core.utils.UiText
import com.claude.codex.ai.monitoring.core.utils.resolve
import com.claude.codex.ai.monitoring.presentation.sessiondetail.TimelineItemUiModel

/** One activity event: an icon node on a vertical rail, the title, optional detail and time. */
@Composable
fun TimelineItem(
    item: TimelineItemUiModel,
    modifier: Modifier = Modifier,
    showRail: Boolean = true,
) {
    val accent = item.tone.color()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) {
            Box(
                modifier = Modifier
                    .size(Dimens.TimelineNode)
                    .background(accent.copy(alpha = 0.14f), CircleShape)
                    .border(Dimens.BorderThin, accent.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(item.icon),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(Dimens.IconSm),
                )
            }
            if (showRail) {
                Box(
                    modifier = Modifier
                        .width(Dimens.TimelineRail)
                        .weight(1f)
                        .background(AppTheme.colors.outline),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = Dimens.SpaceLg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = item.time.resolve(),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppTheme.colors.textSecondary,
                )
            }
            if (item.detail != null) {
                Text(
                    text = item.detail,
                    style = MonoTextStyle,
                    color = AppTheme.colors.textSecondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Dimens.SpaceXs),
                )
            }
        }
    }
}

@Preview
@Composable
private fun TimelineItemPreview() {
    AppTheme(darkTheme = true) {
        TimelineItem(
            item = TimelineItemUiModel(
                eventId = "e1",
                icon = R.drawable.ic_tool,
                tone = StatusTone.RUNNING,
                title = "Edit",
                detail = "app/src/main/java/MainActivity.kt",
                time = UiText.Raw("just now"),
            ),
        )
    }
}
