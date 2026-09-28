package com.claude.codex.ai.monitoring.presentation.sessiondetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claude.codex.ai.monitoring.core.utils.Clock
import com.claude.codex.ai.monitoring.core.utils.ticks
import com.claude.codex.ai.monitoring.domain.repo.AgentRepository
import com.claude.codex.ai.monitoring.domain.usecase.ObserveSessionDetailUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SessionDetailViewModel(
    private val sessionId: String,
    observeSessionDetail: ObserveSessionDetailUseCase,
    private val agentRepository: AgentRepository,
    clock: Clock,
) : ViewModel() {

    private val _sessionDetailUiState = MutableStateFlow(SessionDetailUiState(subtitle = sessionId))
    val sessionDetailUiState: StateFlow<SessionDetailUiState> = _sessionDetailUiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(observeSessionDetail(sessionId), clock.ticks()) { detail, now -> detail.toUiState(sessionId, now) }
                .collect { state -> _sessionDetailUiState.update { state } }
        }
    }

    fun onEvent(event: SessionDetailEvent) {
        when (event) {
            SessionDetailEvent.OnRetryClick -> agentRepository.reconnectNow()
        }
    }
}
