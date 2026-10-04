package com.claude.codex.ai.monitoring.presentation.sessiondetail.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.ui.QuickActionChip
import com.claude.codex.ai.monitoring.core.ui.StatusTone

/** Stops Claude while it is working; shown on both tabs so it is always one tap away. */
@Composable
fun StopClaudeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    QuickActionChip(
        text = stringResource(R.string.detail_stop),
        onClick = onClick,
        tone = StatusTone.ERROR,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
    )
}

@Preview
@Composable
private fun StopClaudeButtonPreview() {
    AppTheme(darkTheme = true) {
        StopClaudeButton(onClick = {})
    }
}
