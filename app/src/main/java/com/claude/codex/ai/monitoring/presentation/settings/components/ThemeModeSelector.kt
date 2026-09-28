package com.claude.codex.ai.monitoring.presentation.settings.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.theme.PillShape
import com.claude.codex.ai.monitoring.domain.models.ThemeMode
import com.claude.codex.ai.monitoring.presentation.settings.ThemeOptionUiModel
import com.claude.codex.ai.monitoring.presentation.settings.labelRes

/** Segmented System / Dark / Light control; the selected segment fills with the brand gradient. */
@Composable
fun ThemeModeSelector(
    options: List<ThemeOptionUiModel>,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceElevated, PillShape)
            .border(Dimens.BorderThin, colors.outline, PillShape)
            .padding(Dimens.SpaceXs)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        options.forEach { option ->
            val textColor by animateColorAsState(
                targetValue = if (option.selected) colors.onBrand else colors.textSecondary,
                label = "segmentText",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.TouchTarget - Dimens.SpaceSm)
                    .clip(PillShape)
                    .then(if (option.selected) Modifier.background(colors.brandGradient) else Modifier.background(Color.Transparent))
                    .selectable(
                        selected = option.selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(option.mode) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = stringResource(option.label), style = MaterialTheme.typography.labelLarge, color = textColor)
            }
        }
    }
}

@Preview
@Composable
private fun ThemeModeSelectorPreview() {
    AppTheme(darkTheme = true) {
        ThemeModeSelector(
            options = ThemeMode.entries.map { ThemeOptionUiModel(it, it.labelRes(), it == ThemeMode.DARK) },
            onSelect = {},
        )
    }
}
