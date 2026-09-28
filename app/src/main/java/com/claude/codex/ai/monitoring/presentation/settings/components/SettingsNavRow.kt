package com.claude.codex.ai.monitoring.presentation.settings.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens

/** Row that opens another screen: icon, title, summary and a chevron. */
@Composable
fun SettingsNavRow(
    @DrawableRes icon: Int,
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTarget)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = colors.brandEnd, modifier = Modifier.size(Dimens.IconLg))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            Text(text = summary, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(Dimens.IconMd))
    }
}

@Preview
@Composable
private fun SettingsNavRowPreview() {
    AppTheme(darkTheme = true) {
        SettingsNavRow(icon = R.drawable.ic_shield, title = "Devices & security", summary = "Keys and revoking", onClick = {})
    }
}
