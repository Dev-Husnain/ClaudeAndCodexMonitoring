package com.claude.codex.ai.monitoring.presentation.sessiondetail

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.utils.UiText
import com.claude.codex.ai.monitoring.domain.models.TerminalScreenModel
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
    // Remote control (phase 5)
    val canControl: Boolean = false,
    val showReadOnlyNote: Boolean = false,
    val showComposer: Boolean = false,
    val awayMode: Boolean = false,
    val awayBusy: Boolean = false,
    val awaiting: AwaitingUiModel? = null,
    val composerText: String = "",
    val sending: Boolean = false,
    val deliveryNote: UiText? = null,
    val deliveryTone: StatusTone = StatusTone.STALE,
    /** Started with `agentmon claude`: its terminal can be shown and typed into. */
    val hasTerminal: Boolean = false,
    val tab: DetailTab = DetailTab.ACTIVITY,
    /** Claude is working and this phone may control it: the Stop button is shown. */
    val canStop: Boolean = false,
    val showStopConfirm: Boolean = false,
) {
    /** Copies the fields the screen owns locally (typing, sending) onto a fresh server-derived state. */
    fun withLocalFrom(previous: SessionDetailUiState) = copy(
        awayBusy = previous.awayBusy,
        composerText = previous.composerText,
        sending = previous.sending,
        deliveryNote = previous.deliveryNote,
        deliveryTone = previous.deliveryTone,
        tab = if (hasTerminal) previous.tab else DetailTab.ACTIVITY,
        showStopConfirm = previous.showStopConfirm && canStop,
    )
}

enum class DetailTab { ACTIVITY, TERMINAL }

/** The Terminal tab; [screen] is null until the computer sends the first screen. */
@Immutable
data class TerminalUiState(
    val visible: Boolean = false,
    val screen: TerminalScreenModel? = null,
)

/** Everything the control widgets need, bundled so the list takes one parameter. */
@Immutable
data class SessionControlsUiModel(
    val canControl: Boolean,
    val showReadOnlyNote: Boolean,
    val awayMode: Boolean,
    val awayBusy: Boolean,
    val awaiting: AwaitingUiModel?,
    val busy: Boolean,
)

fun SessionDetailUiState.controls() = SessionControlsUiModel(
    canControl = canControl,
    showReadOnlyNote = showReadOnlyNote,
    awayMode = awayMode,
    awayBusy = awayBusy,
    awaiting = awaiting,
    busy = sending,
)

@Immutable
data class AwaitingUiModel(
    val isPermission: Boolean,
    val detail: String?,
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
