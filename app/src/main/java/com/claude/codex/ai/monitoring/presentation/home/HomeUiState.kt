package com.claude.codex.ai.monitoring.presentation.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.utils.UiText
import com.claude.codex.ai.monitoring.presentation.common.ConnectionUiModel

@Immutable
data class HomeUiState(
    val connection: ConnectionUiModel = ConnectionUiModel.Initial,
    val computerName: String? = null,
    val readOnly: Boolean = false,
    /** This phone may send input, so it can switch Away mode. */
    val canControl: Boolean = false,
    val awayMode: Boolean = false,
    val awayBusy: Boolean = false,
    /** No snapshot yet and still trying: show skeletons. */
    val isLoading: Boolean = true,
    /** No snapshot and the computer is unreachable: show the full offline state. */
    val isOffline: Boolean = false,
    val offlineMessage: UiText? = null,
    /** The computer refused this phone (revoked, forgotten, identity changed). */
    val unauthorized: UnauthorizedUiModel? = null,
    /** Connected but the computer reports no sessions. */
    val isEmpty: Boolean = false,
    /** Unreachable while showing cached data: explains that the data may be old. */
    val staleNotice: UiText? = null,
    val needsYou: List<SessionItemUiModel> = emptyList(),
    val projects: List<ProjectSectionUiModel> = emptyList(),
)

@Immutable
data class UnauthorizedUiModel(
    @param:StringRes val title: Int,
    val message: UiText,
)

@Immutable
data class SessionItemUiModel(
    val sessionId: String,
    val projectName: String,
    val tone: StatusTone,
    val statusLabel: UiText,
    val snippet: String?,
    val lastTool: UiText?,
    val relativeTime: UiText,
    val isMonitorOnly: Boolean,
)

@Immutable
data class ProjectSectionUiModel(
    val projectId: String,
    val name: String,
    val sessions: List<SessionItemUiModel>,
)
