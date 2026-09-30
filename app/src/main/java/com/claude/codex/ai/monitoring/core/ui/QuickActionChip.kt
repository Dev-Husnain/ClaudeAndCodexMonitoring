package com.claude.codex.ai.monitoring.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.theme.PillShape

/** Tinted pill button for quick answers (Deny, Stop, Continue…) with press feedback. */
@Composable
fun QuickActionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.BRAND,
    enabled: Boolean = true,
) {
    val accent = tone.color()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, spring(), label = "chipScale")
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(Dimens.BorderThin, accent.copy(alpha = 0.45f)),
        interactionSource = interaction,
        modifier = modifier.scale(scale).alpha(if (enabled) 1f else DISABLED_ALPHA),
    ) {
        Box(modifier = Modifier.heightIn(min = Dimens.TouchTarget).padding(horizontal = Dimens.SpaceLg), contentAlignment = Alignment.Center) {
            Text(text = text, style = MaterialTheme.typography.labelLarge, color = accent)
        }
    }
}

private const val PRESSED_SCALE = 0.95f
private const val DISABLED_ALPHA = 0.5f

@Preview
@Composable
private fun QuickActionChipPreview() {
    AppTheme(darkTheme = true) {
        QuickActionChip(text = "Deny", onClick = {}, tone = StatusTone.ERROR)
    }
}
