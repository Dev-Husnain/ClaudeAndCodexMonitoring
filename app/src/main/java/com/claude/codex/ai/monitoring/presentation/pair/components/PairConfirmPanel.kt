package com.claude.codex.ai.monitoring.presentation.pair.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.ui.AppTextField
import com.claude.codex.ai.monitoring.core.ui.GradientButton
import com.claude.codex.ai.monitoring.core.ui.SurfaceCard
import com.claude.codex.ai.monitoring.presentation.pair.PairOfferUiModel

/** Shows both keys to compare by eye, lets the owner name the phone, and sends the request. */
@Composable
fun PairConfirmPanel(
    offer: PairOfferUiModel,
    deviceName: String,
    @StringRes nameError: Int?,
    onDeviceNameChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier.padding(horizontal = Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
    ) {
        Text(
            text = stringResource(R.string.pair_confirm_title, offer.computerName),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
        )
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            KeyValueBlock(label = stringResource(R.string.pair_confirm_address), value = offer.address, hint = null)
            KeyValueBlock(
                label = stringResource(R.string.pair_confirm_desktop_key),
                value = offer.desktopKey,
                hint = stringResource(R.string.pair_confirm_desktop_key_hint),
                modifier = Modifier.padding(top = Dimens.SpaceLg),
            )
            KeyValueBlock(
                label = stringResource(R.string.pair_confirm_phone_key),
                value = offer.phoneKey,
                hint = stringResource(R.string.pair_confirm_phone_key_hint),
                modifier = Modifier.padding(top = Dimens.SpaceLg),
            )
        }
        AppTextField(
            value = deviceName,
            onValueChange = onDeviceNameChange,
            label = stringResource(R.string.pair_device_name),
            errorText = nameError?.let { stringResource(it) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            onDone = onSend,
            modifier = Modifier.fillMaxWidth(),
        )
        GradientButton(
            text = stringResource(R.string.pair_send),
            onClick = onSend,
            leadingIcon = R.drawable.ic_shield,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview
@Composable
private fun PairConfirmPanelPreview() {
    AppTheme(darkTheme = true) {
        PairConfirmPanel(
            offer = PairOfferUiModel("HUSSNAIN-MEHDI", "agent.appsdev.qzz.io", "ab12 cd34 ef56 7890", "9f8e 7d6c 5b4a 3210"),
            deviceName = "Redmi Note 10",
            nameError = null,
            onDeviceNameChange = {},
            onSend = {},
        )
    }
}
