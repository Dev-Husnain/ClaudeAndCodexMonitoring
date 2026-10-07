package com.claude.codex.ai.monitoring.presentation.computerprojects

import androidx.compose.runtime.Immutable
import com.claude.codex.ai.monitoring.core.utils.UiText

@Immutable
data class ComputerProjectsUiState(
    val isLoading: Boolean = true,
    /** The computer did not answer (offline, or an older desktop agent). */
    val isUnavailable: Boolean = false,
    /** This phone may only see chosen projects, so it is not shown the others. */
    val notAllowed: Boolean = false,
    val projects: List<ComputerProjectItemUiModel> = emptyList(),
    /** This phone may send input, so it may start watching a project. */
    val canAdd: Boolean = false,
    /** The project being added, while the computer works on it. */
    val addingId: String? = null,
    val error: UiText? = null,
)

@Immutable
data class ComputerProjectItemUiModel(
    val projectId: String,
    val name: String,
    val pathHint: String,
    /** e.g. "12 conversations · 2 h ago". */
    val detail: UiText,
    val monitored: Boolean,
)
