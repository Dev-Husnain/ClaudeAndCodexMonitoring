package com.claude.codex.ai.monitoring.presentation.pair.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.theme.LocalReduceMotion
import com.claude.codex.ai.monitoring.core.ui.GradientButton

/** Springy check mark, what the phone may do, and the way into the app. */
@Composable
fun PairSuccessPanel(
    computerName: String,
    canSendInput: Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val scale = remember { Animatable(if (reduceMotion) 1f else 0.4f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Column(
        modifier = modifier.padding(Dimens.SpaceXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
    ) {
        Box(
            modifier = Modifier
                .scale(scale.value)
                .size(Dimens.SpaceHuge * 2)
                .background(colors.done.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(Dimens.SpaceHuge + Dimens.SpaceMd).background(colors.done, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.onBrand, modifier = Modifier.size(Dimens.IconXl))
            }
        }
        Text(
            text = stringResource(R.string.pair_success_title, computerName),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(if (canSendInput) R.string.pair_success_input else R.string.pair_success_read_only),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        GradientButton(
            text = stringResource(R.string.pair_success_cta),
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpaceSm),
        )
    }
}

@Preview
@Composable
private fun PairSuccessPanelPreview() {
    AppTheme(darkTheme = true) {
        PairSuccessPanel(computerName = "HUSSNAIN-MEHDI", canSendInput = false, onContinue = {})
    }
}
