package com.claude.codex.ai.monitoring.presentation.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens

/** Skeleton list shown until the first snapshot arrives. */
@Composable
fun HomeLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        repeat(SKELETON_COUNT) { SessionCardSkeleton() }
    }
}

private const val SKELETON_COUNT = 4

@Preview
@Composable
private fun HomeLoadingPreview() {
    AppTheme(darkTheme = true) {
        HomeLoading()
    }
}
