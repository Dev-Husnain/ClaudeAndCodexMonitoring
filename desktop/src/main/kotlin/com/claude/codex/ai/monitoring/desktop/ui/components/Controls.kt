package com.claude.codex.ai.monitoring.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.claude.codex.ai.monitoring.desktop.ui.theme.DesktopTheme

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = DesktopTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) colors.brandGradient else androidx.compose.ui.graphics.SolidColor(colors.outline))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = colors.onBrand)
    }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = DesktopTheme.colors.textPrimary) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** Selectable chip, used for project access and filters. */
@Composable
fun ToggleChip(text: String, selected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DesktopTheme.colors
    val accent = if (selected) colors.brandEnd else colors.textSecondary
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = accent,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.brandEnd.copy(alpha = 0.14f) else colors.surfaceElevated)
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(50))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun SwitchRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = DesktopTheme.colors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.brandStart, uncheckedTrackColor = colors.surfaceElevated),
        )
    }
}
