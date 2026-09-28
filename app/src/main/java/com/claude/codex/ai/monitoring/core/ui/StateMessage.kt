package com.claude.codex.ai.monitoring.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens

/**
 * Empty, error and offline states: a glowing icon medallion, a title, a message and an optional
 * action. One component, parameterised, instead of three near-duplicates.
 */
@Composable
fun StateMessage(
    @DrawableRes icon: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.BRAND,
    actionLabel: String? = null,
    @DrawableRes actionIcon: Int? = null,
    onAction: (() -> Unit)? = null,
) {
    val accent = tone.color()
    Column(
        modifier = modifier.padding(Dimens.SpaceXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.SpaceHuge * 2)
                .background(
                    Brush.radialGradient(listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0f))),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.SpaceHuge + Dimens.SpaceLg)
                    .background(AppTheme.colors.surfaceElevated, CircleShape)
                    .border(Dimens.BorderThin, accent.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(Dimens.IconLg),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (actionLabel != null && onAction != null) {
            GradientButton(
                text = actionLabel,
                onClick = onAction,
                leadingIcon = actionIcon,
                modifier = Modifier.padding(top = Dimens.SpaceSm),
            )
        }
    }
}

@Preview
@Composable
private fun StateMessagePreview() {
    AppTheme(darkTheme = true) {
        StateMessage(
            icon = R.drawable.ic_offline,
            title = "Computer unreachable",
            message = "Check that the desktop agent is running.",
            tone = StatusTone.ERROR,
            actionLabel = "Try again",
            actionIcon = R.drawable.ic_refresh,
            onAction = {},
        )
    }
}
