package com.claude.codex.ai.monitoring.presentation.sessiondetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.utils.Clock
import com.claude.codex.ai.monitoring.core.utils.UiText
import com.claude.codex.ai.monitoring.core.utils.ticks
import com.claude.codex.ai.monitoring.domain.models.SessionModel
import com.claude.codex.ai.monitoring.domain.models.DeliveryStatus
import com.claude.codex.ai.monitoring.domain.models.QuickActionType
import com.claude.codex.ai.monitoring.domain.models.TerminalKeyType
import com.claude.codex.ai.monitoring.domain.repo.AgentRepository
import com.claude.codex.ai.monitoring.domain.repo.SettingsRepository
import com.claude.codex.ai.monitoring.domain.usecase.ObserveSessionDetailUseCase
import com.claude.codex.ai.monitoring.domain.usecase.SendInstructionUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDetailViewModel(
    private val sessionId: String,
    observeSessionDetail: ObserveSessionDetailUseCase,
    private val agentRepository: AgentRepository,
    private val sendInstruction: SendInstructionUseCase,
    private val settingsRepository: SettingsRepository,
    clock: Clock,
) : ViewModel() {

    private val _sessionDetailUiState = MutableStateFlow(SessionDetailUiState())
    val sessionDetailUiState: StateFlow<SessionDetailUiState> = _sessionDetailUiState.asStateFlow()

    private val _effects = Channel<SessionDetailEffect>(Channel.BUFFERED)
    val effects: Flow<SessionDetailEffect> = _effects.receiveAsFlow()

    /**
     * The terminal, attached on the computer only while the Terminal tab is open and the screen is
     * collected (WhileSubscribed), so nothing streams in the background.
     */
    val terminalUiState: StateFlow<TerminalUiState> = _sessionDetailUiState
        .map { it.hasTerminal && it.tab == DetailTab.TERMINAL }
        .distinctUntilChanged()
        .flatMapLatest { visible ->
            if (visible) agentRepository.terminal(sessionId).map { TerminalUiState(visible = true, screen = it) } else flowOf(TerminalUiState())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TerminalUiState())

    init {
        viewModelScope.launch {
            combine(observeSessionDetail(sessionId), clock.ticks()) { detail, now ->
                session = detail.session
                detail.toUiState(now)
            }
                .collect { fresh -> _sessionDetailUiState.update { previous -> fresh.withLocalFrom(previous) } }
        }
    }

    fun onEvent(event: SessionDetailEvent) {
        when (event) {
            SessionDetailEvent.OnRetryClick -> agentRepository.reconnectNow()
            is SessionDetailEvent.OnComposerChange -> _sessionDetailUiState.update {
                it.copy(composerText = event.text, deliveryNote = if (it.sending) it.deliveryNote else null)
            }
            SessionDetailEvent.OnSendClick -> send()
            is SessionDetailEvent.OnQuickAction -> quickAction(event.action)
            is SessionDetailEvent.OnAwayModeToggle -> setAwayMode(event.enabled)
            is SessionDetailEvent.OnTabSelect -> _sessionDetailUiState.update {
                it.copy(tab = if (it.hasTerminal) event.tab else DetailTab.ACTIVITY)
            }
            is SessionDetailEvent.OnTerminalKey -> pressKey(event.key)
            SessionDetailEvent.OnStopClick -> _sessionDetailUiState.update { it.copy(showStopConfirm = it.canStop) }
            SessionDetailEvent.OnStopDismiss -> _sessionDetailUiState.update { it.copy(showStopConfirm = false) }
            SessionDetailEvent.OnStopConfirm -> stop()
            SessionDetailEvent.OnStartTerminalClick -> startTerminal()
        }
    }

    private fun send() {
        val text = _sessionDetailUiState.value.composerText
        if (text.isBlank() || _sessionDetailUiState.value.sending) return
        startSending()
        viewModelScope.launch {
            val status = sendInstruction(sessionId, text) ?: return@launch finishSending(null, clearComposer = false)
            finishSending(status, clearComposer = status !is DeliveryStatus.Failed)
        }
    }

    private fun quickAction(action: QuickActionType) {
        if (_sessionDetailUiState.value.sending) return
        startSending()
        viewModelScope.launch { finishSending(agentRepository.quickAction(sessionId, action), clearComposer = false) }
    }

    private fun stop() {
        _sessionDetailUiState.update { it.copy(showStopConfirm = false) }
        if (_sessionDetailUiState.value.sending) return
        startSending()
        viewModelScope.launch {
            val status = agentRepository.quickAction(sessionId, QuickActionType.INTERRUPT)
            finishSending(status, clearComposer = false, note = status.toStopNote())
        }
    }

    /** The newest session from the computer, for requests that need its project or Claude's id. */
    private var session: SessionModel? = null

    private fun startTerminal() {
        val current = session ?: return
        if (_sessionDetailUiState.value.startingTerminal) return
        _sessionDetailUiState.update { it.copy(startingTerminal = true, deliveryNote = UiText.Res(R.string.detail_terminal_starting), deliveryTone = StatusTone.STALE) }
        viewModelScope.launch {
            // Claude's own id: recorded for terminals, and the session id itself for plain hooked sessions.
            val claudeId = current.claudeSessionId ?: current.sessionId.takeIf { CLAUDE_ID.matches(it) }
            val outcome = agentRepository.startTerminal(current.projectId, claudeId)
            val opened = outcome.sessionId
            if (outcome.status !is DeliveryStatus.Failed && opened != null) {
                _sessionDetailUiState.update { it.copy(startingTerminal = false, deliveryNote = null) }
                _effects.send(SessionDetailEffect.OpenSession(opened))
            } else {
                val failure = outcome.status as? DeliveryStatus.Failed
                val note = failure?.detail?.let { UiText.Raw(it) } ?: UiText.Res(R.string.delivery_rejected)
                _sessionDetailUiState.update { it.copy(startingTerminal = false, deliveryNote = note, deliveryTone = StatusTone.ERROR) }
            }
        }
    }

    private fun pressKey(key: TerminalKeyType) {
        viewModelScope.launch {
            val status = agentRepository.pressKey(sessionId, key)
            // Keys are fast and frequent: only a failure is worth a note.
            if (status is DeliveryStatus.Failed) finishSending(status, clearComposer = false)
        }
    }

    private fun setAwayMode(enabled: Boolean) {
        _sessionDetailUiState.update { it.copy(awayBusy = true) }
        viewModelScope.launch {
            val status = agentRepository.setAwayMode(enabled)
            _sessionDetailUiState.update {
                it.copy(
                    awayBusy = false,
                    deliveryNote = if (status is DeliveryStatus.Failed) UiText.Res(R.string.away_mode_failed) else it.deliveryNote,
                    deliveryTone = if (status is DeliveryStatus.Failed) StatusTone.ERROR else it.deliveryTone,
                )
            }
        }
    }

    private fun startSending() {
        _sessionDetailUiState.update { it.copy(sending = true, deliveryNote = UiText.Res(R.string.delivery_sending), deliveryTone = StatusTone.STALE) }
    }

    private suspend fun finishSending(
        status: DeliveryStatus?,
        clearComposer: Boolean,
        note: Pair<UiText, StatusTone>? = status?.toNote(),
    ) {
        _sessionDetailUiState.update {
            it.copy(
                sending = false,
                composerText = if (clearComposer) "" else it.composerText,
                deliveryNote = note?.first,
                deliveryTone = note?.second ?: StatusTone.STALE,
            )
        }
        if (status != null && settingsRepository.settings.first().hapticsEnabled) {
            _effects.send(SessionDetailEffect.Haptic(success = status !is DeliveryStatus.Failed))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val CLAUDE_ID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}
