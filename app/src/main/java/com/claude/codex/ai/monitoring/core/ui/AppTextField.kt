package com.claude.codex.ai.monitoring.core.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.core.theme.AppTheme

/** Outlined text field in the app's colours, with optional supporting or error text. */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    onDone: () -> Unit = {},
) {
    val colors = AppTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = (errorText ?: supportingText)?.let { text -> { Text(text) } },
        isError = errorText != null,
        singleLine = singleLine,
        minLines = minLines,
        textStyle = textStyle.copy(color = colors.textPrimary),
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.brandStart,
            unfocusedBorderColor = colors.outline,
            focusedLabelColor = colors.brandStart,
            unfocusedLabelColor = colors.textSecondary,
            cursorColor = colors.brandStart,
            focusedSupportingTextColor = colors.textSecondary,
            unfocusedSupportingTextColor = colors.textSecondary,
        ),
        modifier = modifier,
    )
}

@Preview
@Composable
private fun AppTextFieldPreview() {
    AppTheme(darkTheme = true) {
        AppTextField(value = "Pixel 9", onValueChange = {}, label = "Name shown on your computer")
    }
}
