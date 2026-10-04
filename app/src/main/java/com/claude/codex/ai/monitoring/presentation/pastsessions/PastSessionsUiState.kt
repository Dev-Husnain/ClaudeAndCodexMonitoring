package com.claude.codex.ai.monitoring.presentation.pastsessions

import androidx.compose.runtime.Immutable
import com.claude.codex.ai.monitoring.core.utils.UiText

@Immutable
data class PastSessionsUiState(
    val projectName: String = "",
    val isLoading: Boolean = true,
    /** The computer did not answer (offline, or an older desktop agent). */
    val isUnavailable: Boolean = false,
    val sessions: List<PastSessionItemUiModel> = emptyList(),
    /** This phone may send input, so it may resume. */
    val canResume: Boolean = false,
    val resumeTarget: PastSessionItemUiModel? = null,
    val resumeText: String = "",
    val resuming: Boolean = false,
    val resumeError: UiText? = null,
    val removeTarget: PastSessionItemUiModel? = null,
)

@Immutable
data class PastSessionItemUiModel(
    val claudeSessionId: String,
    val title: String,
    val relativeTime: UiText,
    val lastActiveAtMs: Long,
    /** The AgentMon session showing this conversation while Claude still has it open; tapping opens it. */
    val openSessionId: String?,
)
