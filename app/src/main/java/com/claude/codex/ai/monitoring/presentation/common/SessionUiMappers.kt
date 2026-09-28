package com.claude.codex.ai.monitoring.presentation.common

import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.utils.UiText
import com.claude.codex.ai.monitoring.domain.models.ConnectionStatus
import com.claude.codex.ai.monitoring.domain.models.SessionStatus

fun SessionStatus.toTone(): StatusTone = when (this) {
    SessionStatus.RUNNING -> StatusTone.RUNNING
    SessionStatus.WAITING_INPUT -> StatusTone.WAITING
    SessionStatus.IDLE, SessionStatus.ENDED -> StatusTone.DONE
    SessionStatus.ERROR -> StatusTone.ERROR
    SessionStatus.STALE -> StatusTone.STALE
}

fun SessionStatus.toLabel(): UiText = UiText.Res(
    when (this) {
        SessionStatus.RUNNING -> R.string.status_running
        SessionStatus.WAITING_INPUT -> R.string.status_waiting
        SessionStatus.IDLE -> R.string.status_idle
        SessionStatus.ERROR -> R.string.status_error
        SessionStatus.ENDED -> R.string.status_ended
        SessionStatus.STALE -> R.string.status_stale
    },
)

fun ConnectionStatus.toUiModel(): ConnectionUiModel = when (this) {
    ConnectionStatus.Connecting -> ConnectionUiModel(StatusTone.STALE, UiText.Res(R.string.connection_connecting))
    is ConnectionStatus.Connected -> ConnectionUiModel(StatusTone.DONE, UiText.Res(R.string.connection_connected))
    is ConnectionStatus.Reconnecting -> ConnectionUiModel(StatusTone.WAITING, UiText.Res(R.string.connection_reconnecting))
    is ConnectionStatus.Offline -> ConnectionUiModel(StatusTone.ERROR, UiText.Res(R.string.connection_offline))
    is ConnectionStatus.Unauthorized -> ConnectionUiModel(StatusTone.ERROR, UiText.Res(R.string.connection_unauthorized))
}
