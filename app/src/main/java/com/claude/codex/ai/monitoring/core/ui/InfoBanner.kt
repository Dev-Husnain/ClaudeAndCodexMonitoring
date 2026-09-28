package com.claude.codex.ai.monitoring.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens

/** Inline tinted notice (offline, monitor-only, read-only…). */
@Composable
fun InfoBanner(
    @DrawableRes icon: Int,
    text: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.WAITING,
) {
    val accent = tone.color()
    val shape = MaterialTheme.shapes.small
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.10f), shape)
            .border(Dimens.BorderThin, accent.copy(alpha = 0.30f), shape)
            .padding(horizontal = Dimens.SpaceMd, vertical = Dimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(Dimens.IconSm),
        )
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textPrimary)
    }
}

@Preview
@Composable
private fun InfoBannerPreview() {
    AppTheme(darkTheme = true) {
        InfoBanner(icon = R.drawable.ic_offline, text = "Offline. Showing the last known state.", tone = StatusTone.ERROR)
    }
}
