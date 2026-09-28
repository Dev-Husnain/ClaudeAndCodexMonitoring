package com.claude.codex.ai.monitoring.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.theme.PillShape

/** Tinted pill with a status orb and a label. Used for the connection state and session status. */
@Composable
fun StatusPill(
    tone: StatusTone,
    label: String,
    modifier: Modifier = Modifier,
    animateOrb: Boolean = true,
) {
    val toneColor by animateColorAsState(targetValue = tone.color(), label = "pillColor")
    Row(
        modifier = modifier
            .background(toneColor.copy(alpha = 0.12f), PillShape)
            .border(Dimens.BorderThin, toneColor.copy(alpha = 0.35f), PillShape)
            .padding(start = Dimens.SpaceXs, end = Dimens.SpaceMd, top = Dimens.SpaceXxs, bottom = Dimens.SpaceXxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXxs),
    ) {
        StatusOrb(tone = tone, size = Dimens.OrbSm, animated = animateOrb)
        AnimatedContent(
            targetState = label,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "pillLabel",
        ) { text ->
            Text(text = text, style = MaterialTheme.typography.labelMedium, color = toneColor)
        }
    }
}

@Preview
@Composable
private fun StatusPillPreview() {
    AppTheme(darkTheme = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            StatusPill(tone = StatusTone.DONE, label = "Connected")
            StatusPill(tone = StatusTone.WAITING, label = "Needs you")
            StatusPill(tone = StatusTone.ERROR, label = "Offline")
        }
    }
}
