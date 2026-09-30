package com.claude.codex.ai.monitoring.presentation.sessiondetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.ui.QuickActionChip
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.domain.models.TerminalKeyType

/** Keys Claude's terminal needs that a phone keyboard lacks: Esc, arrows, Shift+Tab, menu digits… */
@Composable
fun TerminalKeysRow(
    enabled: Boolean,
    onKey: (TerminalKeyType) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding),
    ) {
        items(TerminalKeyType.entries, key = { it.name }) { key ->
            val description = stringResource(key.description())
            QuickActionChip(
                text = stringResource(key.label()),
                onClick = { onKey(key) },
                enabled = enabled,
                tone = if (key == TerminalKeyType.CTRL_C) StatusTone.ERROR else StatusTone.BRAND,
                modifier = Modifier.semantics { contentDescription = description },
            )
        }
    }
}

private fun TerminalKeyType.label(): Int = when (this) {
    TerminalKeyType.ENTER -> R.string.key_enter
    TerminalKeyType.ESCAPE -> R.string.key_escape
    TerminalKeyType.TAB -> R.string.key_tab
    TerminalKeyType.SHIFT_TAB -> R.string.key_shift_tab
    TerminalKeyType.UP -> R.string.key_up
    TerminalKeyType.DOWN -> R.string.key_down
    TerminalKeyType.CTRL_C -> R.string.key_ctrl_c
    TerminalKeyType.DIGIT_1 -> R.string.key_1
    TerminalKeyType.DIGIT_2 -> R.string.key_2
    TerminalKeyType.DIGIT_3 -> R.string.key_3
}

private fun TerminalKeyType.description(): Int = when (this) {
    TerminalKeyType.ENTER -> R.string.key_enter_description
    TerminalKeyType.ESCAPE -> R.string.key_escape_description
    TerminalKeyType.TAB -> R.string.key_tab_description
    TerminalKeyType.SHIFT_TAB -> R.string.key_shift_tab_description
    TerminalKeyType.UP -> R.string.key_up_description
    TerminalKeyType.DOWN -> R.string.key_down_description
    TerminalKeyType.CTRL_C -> R.string.key_ctrl_c_description
    TerminalKeyType.DIGIT_1 -> R.string.key_1_description
    TerminalKeyType.DIGIT_2 -> R.string.key_2_description
    TerminalKeyType.DIGIT_3 -> R.string.key_3_description
}

@Preview
@Composable
private fun TerminalKeysRowPreview() {
    AppTheme(darkTheme = true) {
        TerminalKeysRow(enabled = true, onKey = {})
    }
}
