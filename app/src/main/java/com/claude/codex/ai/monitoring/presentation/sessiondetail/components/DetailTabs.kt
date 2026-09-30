package com.claude.codex.ai.monitoring.presentation.sessiondetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.theme.PillShape
import com.claude.codex.ai.monitoring.presentation.sessiondetail.DetailTab

/** Activity / Terminal switch, shown for sessions started with `agentmon claude`. */
@Composable
fun DetailTabs(
    selected: DetailTab,
    onSelect: (DetailTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(AppTheme.colors.surface)
            .border(Dimens.BorderThin, AppTheme.colors.outline, PillShape)
            .padding(Dimens.SpaceXs)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        DetailTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Text(
                text = stringResource(if (tab == DetailTab.ACTIVITY) R.string.detail_tab_activity else R.string.detail_tab_terminal),
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) AppTheme.colors.brandStart else AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(PillShape)
                    .background(if (isSelected) AppTheme.colors.brandStart.copy(alpha = SELECTED_ALPHA) else AppTheme.colors.surface)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
                    .heightIn(min = Dimens.TouchTarget)
                    .wrapContentHeight(Alignment.CenterVertically),
            )
        }
    }
}

private const val SELECTED_ALPHA = 0.14f

@Preview
@Composable
private fun DetailTabsPreview() {
    AppTheme(darkTheme = true) {
        DetailTabs(selected = DetailTab.TERMINAL, onSelect = {})
    }
}
