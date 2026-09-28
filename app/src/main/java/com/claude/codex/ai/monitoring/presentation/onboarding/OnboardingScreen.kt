package com.claude.codex.ai.monitoring.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.ui.AuroraBackground
import com.claude.codex.ai.monitoring.core.ui.GradientButton
import com.claude.codex.ai.monitoring.core.ui.InfoBanner
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.presentation.onboarding.components.FeatureRow
import com.claude.codex.ai.monitoring.presentation.onboarding.components.OnboardingHero

/** First run: what AgentMon does, the monitor-only caveat (spec 7.4), and the way to pair. */
@Composable
fun OnboardingScreen(
    onPairClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuroraBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxSize()
                .systemBarsPadding()
                .widthIn(max = Dimens.ContentMaxWidth)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceXxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXxl),
        ) {
            OnboardingHero()
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                Text(
                    text = stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.headlineMedium.copy(brush = AppTheme.colors.brandGradient),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.onboarding_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXl), modifier = Modifier.fillMaxWidth()) {
                FeatureRow(
                    icon = R.drawable.ic_activity,
                    title = stringResource(R.string.onboarding_feature_live_title),
                    body = stringResource(R.string.onboarding_feature_live_body),
                    tone = StatusTone.RUNNING,
                )
                FeatureRow(
                    icon = R.drawable.ic_bell,
                    title = stringResource(R.string.onboarding_feature_attention_title),
                    body = stringResource(R.string.onboarding_feature_attention_body),
                    tone = StatusTone.WAITING,
                )
                FeatureRow(
                    icon = R.drawable.ic_shield,
                    title = stringResource(R.string.onboarding_feature_private_title),
                    body = stringResource(R.string.onboarding_feature_private_body),
                    tone = StatusTone.DONE,
                )
            }
            InfoBanner(icon = R.drawable.ic_terminal, text = stringResource(R.string.onboarding_monitor_only_note), tone = StatusTone.STALE)
            GradientButton(
                text = stringResource(R.string.onboarding_cta),
                onClick = onPairClick,
                leadingIcon = R.drawable.ic_scan,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
