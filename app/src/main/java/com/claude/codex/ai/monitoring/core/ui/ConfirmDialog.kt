package com.claude.codex.ai.monitoring.core.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.core.theme.AppTheme

/** Two-button confirmation; [destructive] tints the confirm action in the error colour. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val colors = AppTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = colors.surfaceElevated,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) colors.error else colors.brandEnd)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel, color = colors.textSecondary) }
        },
    )
}

@Preview
@Composable
private fun ConfirmDialogPreview() {
    AppTheme(darkTheme = true) {
        ConfirmDialog("Unpair this phone?", "This phone forgets the computer.", "Unpair", "Cancel", {}, {}, destructive = true)
    }
}
