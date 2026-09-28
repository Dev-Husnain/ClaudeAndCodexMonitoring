package com.claude.codex.ai.monitoring.presentation.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.ui.SkeletonBlock
import com.claude.codex.ai.monitoring.core.ui.SurfaceCard

/** Loading placeholder shaped like a [SessionCard]. */
@Composable
fun SessionCardSkeleton(modifier: Modifier = Modifier) {
    SurfaceCard(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
        ) {
            SkeletonBlock(modifier = Modifier.size(Dimens.OrbLg), shape = CircleShape)
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm), modifier = Modifier.weight(1f)) {
                SkeletonBlock(modifier = Modifier.fillMaxWidth(0.5f).height(Dimens.SkeletonLine))
                SkeletonBlock(modifier = Modifier.fillMaxWidth(0.3f).height(Dimens.SkeletonLine))
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpaceMd),
        ) {
            SkeletonBlock(modifier = Modifier.fillMaxWidth().height(Dimens.SkeletonLine))
            SkeletonBlock(modifier = Modifier.fillMaxWidth(0.8f).height(Dimens.SkeletonLine))
        }
    }
}

@Preview
@Composable
private fun SessionCardSkeletonPreview() {
    AppTheme(darkTheme = true) {
        SessionCardSkeleton()
    }
}
