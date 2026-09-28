package com.claude.codex.ai.monitoring.presentation.pair.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.theme.MonoTextStyle

/** Small uppercase label, a monospace value (a key or address) and an optional hint. */
@Composable
fun KeyValueBlock(
    label: String,
    value: String,
    hint: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = label.uppercase(), style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
        Text(
            text = value,
            style = MonoTextStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.padding(top = Dimens.SpaceXs),
        )
        if (hint != null) {
            Text(text = hint, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
        }
    }
}

@Preview
@Composable
private fun KeyValueBlockPreview() {
    AppTheme(darkTheme = true) {
        KeyValueBlock(label = "Computer key", value = "ab12 cd34 ef56 7890", hint = "Must match your computer.")
    }
}
