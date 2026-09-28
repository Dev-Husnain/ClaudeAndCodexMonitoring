package com.claude.codex.ai.monitoring.presentation.settings.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.theme.MonoTextStyle
import com.claude.codex.ai.monitoring.core.ui.GradientButton

/** Desktop agent address with inline validation feedback and a save action. */
@Composable
fun ServerUrlField(
    value: String,
    @StringRes error: Int?,
    saved: Boolean,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.settings_server_url)) },
            singleLine = true,
            isError = error != null,
            textStyle = MonoTextStyle.copy(color = colors.textPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { onSave() }),
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.brandStart,
                unfocusedBorderColor = colors.outline,
                focusedLabelColor = colors.brandStart,
                unfocusedLabelColor = colors.textSecondary,
                cursorColor = colors.brandStart,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        val (message, color) = when {
            error != null -> stringResource(error) to colors.error
            saved -> stringResource(R.string.settings_server_url_saved) to colors.done
            else -> stringResource(R.string.settings_server_url_hint) to colors.textSecondary
        }
        Text(text = message, style = MaterialTheme.typography.bodySmall, color = color)
        GradientButton(
            text = stringResource(R.string.action_save),
            onClick = onSave,
            leadingIcon = R.drawable.ic_check,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview
@Composable
private fun ServerUrlFieldPreview() {
    AppTheme(darkTheme = true) {
        ServerUrlField(
            value = "ws://127.0.0.1:8787/ws",
            error = null,
            saved = false,
            onValueChange = {},
            onSave = {},
        )
    }
}
