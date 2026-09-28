package com.claude.codex.ai.monitoring.presentation.onboarding.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.ui.color

/** Icon tile plus a title and one-line explanation. */
@Composable
fun FeatureRow(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.BRAND,
) {
    val accent = tone.color()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.SpaceHuge - Dimens.SpaceXs)
                .background(accent.copy(alpha = 0.14f), MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painter = painterResource(icon), contentDescription = null, tint = accent, modifier = Modifier.size(Dimens.IconMd))
        }
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXxs)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        }
    }
}

@Preview
@Composable
private fun FeatureRowPreview() {
    AppTheme(darkTheme = true) {
        FeatureRow(icon = R.drawable.ic_shield, title = "Private to your phone", body = "Only phones you approve can connect.")
    }
}
