package com.claude.codex.ai.monitoring.presentation.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.ui.SectionHeader
import com.claude.codex.ai.monitoring.core.ui.SurfaceCard

/** Titled group of settings rows inside a card. */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        SectionHeader(title = title)
        SurfaceCard(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Preview
@Composable
private fun SettingsSectionPreview() {
    AppTheme(darkTheme = true) {
        SettingsSection(title = "Appearance") {
            Text("Row", color = AppTheme.colors.textPrimary)
        }
    }
}
