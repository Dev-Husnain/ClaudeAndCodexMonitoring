package com.claude.codex.ai.monitoring.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.claude.codex.ai.monitoring.core.theme.AppTheme

@Composable
@ReadOnlyComposable
fun StatusTone.color(): Color = when (this) {
    StatusTone.RUNNING -> AppTheme.colors.running
    StatusTone.WAITING -> AppTheme.colors.waiting
    StatusTone.DONE -> AppTheme.colors.done
    StatusTone.ERROR -> AppTheme.colors.error
    StatusTone.STALE -> AppTheme.colors.stale
    StatusTone.BRAND -> AppTheme.colors.brandStart
}
