package com.claude.codex.ai.monitoring.presentation.common

import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.utils.UiText

/** Connection pill content, shared by every screen that shows the link state. */
data class ConnectionUiModel(
    val tone: StatusTone,
    val label: UiText,
) {
    companion object {
        val Initial = ConnectionUiModel(StatusTone.STALE, UiText.Res(R.string.connection_connecting))
    }
}
