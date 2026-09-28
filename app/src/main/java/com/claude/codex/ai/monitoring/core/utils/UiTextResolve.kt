package com.claude.codex.ai.monitoring.core.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/** Resolves the text, including any [UiText] used as a format argument (e.g. "Started %1$s"). */
@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.map { if (it is UiText) it.resolve() else it }.toTypedArray())
    is UiText.Plural -> pluralStringResource(id, count, *args.map { if (it is UiText) it.resolve() else it }.toTypedArray())
    is UiText.Raw -> value
}
