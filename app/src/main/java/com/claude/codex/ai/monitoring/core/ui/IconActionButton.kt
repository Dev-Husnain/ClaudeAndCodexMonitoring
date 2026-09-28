package com.claude.codex.ai.monitoring.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens

/** Round, outlined icon button with a 48dp touch target. */
@Composable
fun IconActionButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(Dimens.TouchTarget),
        shape = CircleShape,
        color = AppTheme.colors.surface,
        border = BorderStroke(Dimens.BorderThin, AppTheme.colors.outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                tint = AppTheme.colors.textPrimary,
                modifier = Modifier.size(Dimens.IconMd),
            )
        }
    }
}

@Preview
@Composable
private fun IconActionButtonPreview() {
    AppTheme(darkTheme = true) {
        IconActionButton(icon = R.drawable.ic_settings, contentDescription = "Settings", onClick = {})
    }
}
