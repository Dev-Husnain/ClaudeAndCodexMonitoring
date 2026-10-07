package com.claude.codex.ai.monitoring.presentation.pastsessions.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.ui.AppTextField

/** Asks for the next instruction for a saved conversation, then resumes it on the computer. */
@Composable
fun ResumeDialog(
    title: String,
    text: String,
    onTextChange: (String) -> Unit,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
    resuming: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
    /** Opens the conversation in a terminal on the computer instead; null hides the option. */
    onOpenTerminal: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = colors.surfaceElevated,
        title = { Text(stringResource(R.string.history_resume_title), style = MaterialTheme.typography.titleLarge, color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                AppTextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = stringResource(R.string.history_resume_label),
                    singleLine = false,
                    minLines = 2,
                    supportingText = stringResource(R.string.history_resume_hint),
                    errorText = error,
                    // Enter starts a new line; Continue sends.
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
                )
                if (onOpenTerminal != null) {
                    TextButton(onClick = onOpenTerminal, enabled = !resuming) {
                        Text(stringResource(R.string.history_open_terminal), color = colors.brandEnd)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onResume, enabled = text.isNotBlank() && !resuming) {
                Text(
                    stringResource(if (resuming) R.string.history_resuming else R.string.history_resume),
                    color = if (text.isNotBlank() && !resuming) colors.brandEnd else colors.textSecondary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !resuming) { Text(stringResource(R.string.action_cancel), color = colors.textSecondary) }
        },
    )
}

@Preview
@Composable
private fun ResumeDialogPreview() {
    AppTheme(darkTheme = true) {
        ResumeDialog("Fix the login bug", "now add tests", {}, {}, {}, resuming = false, error = null)
    }
}
