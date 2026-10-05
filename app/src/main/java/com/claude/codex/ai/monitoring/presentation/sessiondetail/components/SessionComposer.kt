package com.claude.codex.ai.monitoring.presentation.sessiondetail.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.ui.color

/** Sticky message field with a send button and the last delivery result underneath. */
@Composable
fun SessionComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    sending: Boolean,
    note: String?,
    noteTone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val canSend = text.isNotBlank() && !sending
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        AnimatedVisibility(visible = note != null) {
            Text(note.orEmpty(), style = MaterialTheme.typography.labelMedium, color = noteTone.color())
        }
        // Bottom-aligned: the send button stays next to the last line while the message grows.
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            val fieldShape = MaterialTheme.shapes.extraLarge
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.TouchTarget)
                    .background(colors.surfaceElevated, fieldShape)
                    .border(Dimens.BorderThin, colors.outline, fieldShape)
                    .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceMd),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    Text(stringResource(R.string.composer_hint), style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.brandStart),
                    // Enter starts a new line; the button sends.
                    maxLines = MAX_LINES,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            IconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier
                    .size(Dimens.TouchTarget)
                    .background(if (canSend) colors.brandGradient else SolidColor(colors.surfaceElevated), CircleShape),
            ) {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(Dimens.IconMd), color = colors.onBrand, strokeWidth = Dimens.TimelineRail)
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_send),
                        contentDescription = stringResource(R.string.composer_send),
                        tint = if (canSend) colors.onBrand else colors.textSecondary,
                        modifier = Modifier.size(Dimens.IconMd),
                    )
                }
            }
        }
    }
}

private const val MAX_LINES = 6

@Preview
@Composable
private fun SessionComposerPreview() {
    AppTheme(darkTheme = true) {
        SessionComposer(text = "Run the tests again", onTextChange = {}, onSend = {}, sending = false, note = "Delivered to Claude", noteTone = StatusTone.DONE)
    }
}
