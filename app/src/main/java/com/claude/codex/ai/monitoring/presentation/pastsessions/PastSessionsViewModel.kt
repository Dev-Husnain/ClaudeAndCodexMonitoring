package com.claude.codex.ai.monitoring.presentation.pastsessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.utils.Clock
import com.claude.codex.ai.monitoring.core.utils.UiText
import com.claude.codex.ai.monitoring.core.utils.ticks
import com.claude.codex.ai.monitoring.core.utils.toRelativeTime
import com.claude.codex.ai.monitoring.domain.models.DeliveryStatus
import com.claude.codex.ai.monitoring.domain.models.PastSessionModel
import com.claude.codex.ai.monitoring.domain.models.SessionStatus
import com.claude.codex.ai.monitoring.domain.repo.AgentRepository
import com.claude.codex.ai.monitoring.domain.repo.HiddenSessionsRepository
import com.claude.codex.ai.monitoring.domain.repo.hides
import com.claude.codex.ai.monitoring.presentation.sessiondetail.toNote
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A project's saved conversations on the computer, to continue one from the phone (phase 6). */
class PastSessionsViewModel(
    private val projectId: String,
    projectName: String,
    private val agentRepository: AgentRepository,
    private val hiddenSessionsRepository: HiddenSessionsRepository,
    clock: Clock,
) : ViewModel() {

    private val _pastSessionsUiState = MutableStateFlow(PastSessionsUiState(projectName = projectName))
    val pastSessionsUiState: StateFlow<PastSessionsUiState> = _pastSessionsUiState.asStateFlow()

    private val _effects = Channel<PastSessionsEffect>(Channel.BUFFERED)
    val effects: Flow<PastSessionsEffect> = _effects.receiveAsFlow()

    /** null until loaded or when the computer did not answer. */
    private val past = MutableStateFlow<List<PastSessionModel>?>(null)

    init {
        load()
        viewModelScope.launch {
            combine(past, agentRepository.snapshot, hiddenSessionsRepository.hidden, clock.ticks()) { list, snapshot, hidden, now ->
                val open = snapshot.sessions
                    .filter { it.status != SessionStatus.ENDED }
                    .flatMap { session -> listOfNotNull(session.sessionId, session.claudeSessionId).map { it to session.sessionId } }
                    .toMap()
                val items = list.orEmpty()
                    .filterNot { hidden.hides(it.claudeSessionId, it.lastActiveAtMs) }
                    .map { session ->
                        PastSessionItemUiModel(
                            claudeSessionId = session.claudeSessionId,
                            title = session.title,
                            relativeTime = session.lastActiveAtMs.toRelativeTime(now),
                            lastActiveAtMs = session.lastActiveAtMs,
                            openSessionId = open[session.claudeSessionId],
                        )
                    }
                items to snapshot.canSendInput
            }.collect { (items, canResume) ->
                _pastSessionsUiState.update { it.copy(sessions = items, canResume = canResume) }
            }
        }
    }

    fun onEvent(event: PastSessionsEvent) {
        when (event) {
            PastSessionsEvent.OnRetryClick -> load()
            is PastSessionsEvent.OnSessionClick -> {
                val item = item(event.claudeSessionId) ?: return
                val open = item.openSessionId
                if (open != null) {
                    viewModelScope.launch { _effects.send(PastSessionsEffect.OpenSession(open)) }
                } else if (_pastSessionsUiState.value.canResume) {
                    _pastSessionsUiState.update { it.copy(resumeTarget = item, resumeText = "", resumeError = null) }
                }
            }
            is PastSessionsEvent.OnSessionLongClick -> _pastSessionsUiState.update { state ->
                state.copy(removeTarget = item(event.claudeSessionId)?.takeIf { it.openSessionId == null })
            }
            is PastSessionsEvent.OnResumeTextChange -> _pastSessionsUiState.update { it.copy(resumeText = event.text, resumeError = null) }
            PastSessionsEvent.OnResumeDismiss -> _pastSessionsUiState.update { if (it.resuming) it else it.copy(resumeTarget = null) }
            PastSessionsEvent.OnResumeConfirm -> resume()
            PastSessionsEvent.OnRemoveDismiss -> _pastSessionsUiState.update { it.copy(removeTarget = null) }
            PastSessionsEvent.OnRemoveConfirm -> {
                val target = _pastSessionsUiState.value.removeTarget ?: return
                _pastSessionsUiState.update { it.copy(removeTarget = null) }
                viewModelScope.launch { hiddenSessionsRepository.hide(target.claudeSessionId, target.lastActiveAtMs) }
            }
        }
    }

    private fun item(claudeSessionId: String) = _pastSessionsUiState.value.sessions.firstOrNull { it.claudeSessionId == claudeSessionId }

    private fun load() {
        _pastSessionsUiState.update { it.copy(isLoading = true, isUnavailable = false) }
        viewModelScope.launch {
            val sessions = agentRepository.pastSessions(projectId)
            past.value = sessions
            _pastSessionsUiState.update { it.copy(isLoading = false, isUnavailable = sessions == null) }
        }
    }

    private fun resume() {
        val state = _pastSessionsUiState.value
        val target = state.resumeTarget ?: return
        val text = state.resumeText.trim()
        if (text.isEmpty() || state.resuming) return
        _pastSessionsUiState.update { it.copy(resuming = true, resumeError = null) }
        viewModelScope.launch {
            val outcome = agentRepository.resumeSession(projectId, target.claudeSessionId, text)
            val sessionId = outcome.sessionId
            if (outcome.status !is DeliveryStatus.Failed && sessionId != null) {
                _pastSessionsUiState.update { it.copy(resuming = false, resumeTarget = null, resumeText = "") }
                _effects.send(PastSessionsEffect.OpenSession(sessionId))
            } else {
                val failure = outcome.status as? DeliveryStatus.Failed
                // The computer explains why (e.g. "still open"); otherwise fall back to the generic note.
                val message = failure?.detail?.let { UiText.Raw(it) } ?: failure?.toNote()?.first ?: UiText.Res(R.string.delivery_rejected)
                _pastSessionsUiState.update { it.copy(resuming = false, resumeError = message) }
            }
        }
    }
}
