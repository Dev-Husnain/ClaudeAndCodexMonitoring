package com.claude.codex.ai.monitoring.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.claude.codex.ai.monitoring.desktop.ui.theme.DesktopTheme

private val FieldShape = RoundedCornerShape(12.dp)

/**
 * "Phone access address": the owner's tunnel hostname, used in pairing codes for the "Anywhere" route.
 * Read-only when it comes from the command line or `AGENTMON_PUBLIC_URL` ([lockedBy]).
 */
@Composable
fun PublicAddressRow(
    address: String?,
    lockedBy: String?,
    error: String?,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DesktopTheme.colors
    val saved = address?.removePrefix("https://").orEmpty()
    var text by remember { mutableStateOf(saved) }
    LaunchedEffect(saved) { text = saved }
    val changed = text.trim().removePrefix("https://").trimEnd('/') != saved

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Phone access address", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Text(
            when {
                lockedBy != null -> "Set by $lockedBy. Change it there."
                address == null -> "Your Cloudflare tunnel hostname, e.g. agent.example.com. Without it, phones pair over USB only " +
                    "and work only while plugged in. See the README: \"Reach your computer from anywhere\"."
                else -> "Phones paired with \"Anywhere\" reach this computer at https://$saved. Leave it empty to pair over USB only."
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(colors.surfaceElevated, FieldShape)
                    .border(1.dp, if (error != null) colors.error else colors.outline, FieldShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (text.isEmpty()) {
                    Text("agent.example.com", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.replace("\n", "") },
                    enabled = lockedBy == null,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.brandStart),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (changed) onSave(text) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PrimaryButton("Save", onClick = { onSave(text) }, enabled = lockedBy == null && changed)
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error) }
    }
}
