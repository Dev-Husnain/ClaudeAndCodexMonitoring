package com.claude.codex.ai.monitoring.presentation.pair.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.theme.MonoTextStyle
import com.claude.codex.ai.monitoring.core.ui.AppTextField
import com.claude.codex.ai.monitoring.core.ui.GradientButton

/** Paste the pairing code instead of scanning (no camera, emulator, accessibility). */
@Composable
fun CodeEntryPanel(
    value: String,
    @StringRes error: Int?,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = Dimens.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
    ) {
        AppTextField(
            value = value,
            onValueChange = onValueChange,
            label = stringResource(R.string.pair_code_label),
            supportingText = stringResource(R.string.pair_code_hint),
            errorText = error?.let { stringResource(it) },
            singleLine = false,
            minLines = 4,
            textStyle = MonoTextStyle,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            onDone = onSubmit,
            modifier = Modifier.fillMaxWidth(),
        )
        GradientButton(
            text = stringResource(R.string.pair_code_continue),
            onClick = onSubmit,
            enabled = value.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onScanClick) {
            Text(text = stringResource(R.string.pair_scan_instead), color = AppTheme.colors.brandEnd)
        }
    }
}

@Preview
@Composable
private fun CodeEntryPanelPreview() {
    AppTheme(darkTheme = true) {
        CodeEntryPanel(value = "AGENTMON1:eyJ2Ijox", error = null, onValueChange = {}, onSubmit = {}, onScanClick = {})
    }
}
