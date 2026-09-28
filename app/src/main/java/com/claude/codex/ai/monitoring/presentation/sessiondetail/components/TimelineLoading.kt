package com.claude.codex.ai.monitoring.presentation.sessiondetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.ui.SkeletonBlock

/** Skeleton rows while the timeline history loads. */
@Composable
fun TimelineLoading(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg)) {
        repeat(ROWS) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
            ) {
                SkeletonBlock(modifier = Modifier.size(Dimens.TimelineNode), shape = CircleShape)
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm), modifier = Modifier.weight(1f)) {
                    SkeletonBlock(modifier = Modifier.fillMaxWidth(0.6f).height(Dimens.SkeletonLine))
                    SkeletonBlock(modifier = Modifier.fillMaxWidth(0.9f).height(Dimens.SkeletonLine))
                }
            }
        }
    }
}

private const val ROWS = 5

@Preview
@Composable
private fun TimelineLoadingPreview() {
    AppTheme(darkTheme = true) {
        TimelineLoading()
    }
}
