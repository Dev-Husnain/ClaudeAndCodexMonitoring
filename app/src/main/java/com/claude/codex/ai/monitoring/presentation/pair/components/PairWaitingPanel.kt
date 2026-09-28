package com.claude.codex.ai.monitoring.presentation.pair.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.ui.StatusOrb
import com.claude.codex.ai.monitoring.core.ui.StatusTone

/** Waiting for the owner to approve this phone on the computer. */
@Composable
fun PairWaitingPanel(
    computerName: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(Dimens.SpaceXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
    ) {
        StatusOrb(tone = StatusTone.WAITING, size = Dimens.SpaceHuge)
        Text(
            text = stringResource(R.string.pair_waiting_title, computerName),
            style = MaterialTheme.typography.headlineSmall,
            color = AppTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.pair_waiting_message),
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview
@Composable
private fun PairWaitingPanelPreview() {
    AppTheme(darkTheme = true) {
        PairWaitingPanel(computerName = "HUSSNAIN-MEHDI")
    }
}
