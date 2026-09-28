package com.claude.codex.ai.monitoring.presentation.sessiondetail

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.utils.UiText
import com.claude.codex.ai.monitoring.presentation.common.ConnectionUiModel

@Immutable
data class SessionDetailUiState(
    val title: String = "",
    val subtitle: String = "",
    val connection: ConnectionUiModel = ConnectionUiModel.Initial,
    /** Waiting for the first snapshot from the computer. */
    val isLoading: Boolean = true,
    /** The computer does not know this session. */
    val isNotFound: Boolean = false,
    val isOffline: Boolean = false,
    val header: SessionHeaderUiModel? = null,
    /** Newest first; null while history is loading. */
    val timeline: List<TimelineItemUiModel>? = null,
)

@Immutable
data class SessionHeaderUiModel(
    val tone: StatusTone,
    val statusLabel: UiText,
    val started: UiText,
    val lastTool: String?,
    val controlLabel: UiText,
    val controlTone: StatusTone,
    val message: String?,
    val errorInfo: String?,
)

@Immutable
data class TimelineItemUiModel(
    val eventId: String,
    @param:DrawableRes val icon: Int,
    val tone: StatusTone,
    val title: String,
    val detail: String?,
    val time: UiText,
)
