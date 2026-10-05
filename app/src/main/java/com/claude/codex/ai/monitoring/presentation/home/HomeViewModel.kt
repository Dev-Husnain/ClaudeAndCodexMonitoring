package com.claude.codex.ai.monitoring.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claude.codex.ai.monitoring.core.utils.Clock
import com.claude.codex.ai.monitoring.core.utils.ticks
import com.claude.codex.ai.monitoring.domain.repo.AgentRepository
import com.claude.codex.ai.monitoring.domain.repo.HiddenSessionsRepository
import com.claude.codex.ai.monitoring.domain.repo.PairingRepository
import com.claude.codex.ai.monitoring.domain.repo.projectKey
import com.claude.codex.ai.monitoring.domain.usecase.ObserveSessionOverviewUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    observeSessionOverview: ObserveSessionOverviewUseCase,
    private val agentRepository: AgentRepository,
    private val pairingRepository: PairingRepository,
    private val hiddenSessionsRepository: HiddenSessionsRepository,
    clock: Clock,
) : ViewModel() {

    private val _homeUiState = MutableStateFlow(HomeUiState())
    val homeUiState: StateFlow<HomeUiState> = _homeUiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(observeSessionOverview(), pairingRepository.pairing, clock.ticks()) { overview, pairing, now ->
                overview.toHomeUiState(now, pairing?.computerName)
            }.collect { state -> _homeUiState.update { state.copy(awayBusy = it.awayBusy, removeTarget = it.removeTarget, removeProjectTarget = it.removeProjectTarget) } }
        }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.OnRetryClick -> agentRepository.reconnectNow()
            HomeEvent.OnPairAgainClick -> viewModelScope.launch { pairingRepository.unpair() }
            is HomeEvent.OnAwayModeToggle -> {
                _homeUiState.update { it.copy(awayBusy = true) }
                viewModelScope.launch {
                    agentRepository.setAwayMode(event.enabled)
                    // The switch follows the computer's `away.update`, so a failure simply leaves it unchanged.
                    _homeUiState.update { it.copy(awayBusy = false) }
                }
            }
            is HomeEvent.OnRemoveRequest -> _homeUiState.update { state ->
                val session = (state.needsYou + state.projects.flatMap { it.sessions }).firstOrNull { it.sessionId == event.sessionId }
                state.copy(removeTarget = session?.takeIf { it.removable })
            }
            HomeEvent.OnRemoveDismiss -> _homeUiState.update { it.copy(removeTarget = null, removeProjectTarget = null) }
            is HomeEvent.OnRemoveProjectRequest -> _homeUiState.update { state ->
                state.copy(removeProjectTarget = state.projects.firstOrNull { it.projectId == event.projectId && it.sessions.isEmpty() })
            }
            HomeEvent.OnRemoveProjectConfirm -> {
                val target = _homeUiState.value.removeProjectTarget ?: return
                _homeUiState.update { it.copy(removeProjectTarget = null) }
                viewModelScope.launch { hiddenSessionsRepository.hide(projectKey(target.projectId), target.lastActivityMs) }
            }
            HomeEvent.OnRemoveConfirm -> {
                val state = _homeUiState.value
                val target = state.removeTarget ?: return
                _homeUiState.update { it.copy(removeTarget = null) }
                // Everything that belongs to it goes: its conversation in History and, if it was the project's
                // last session on the list, the project heading too (it comes back with the next session there).
                val lastInProject = state.projects.firstOrNull { it.projectId == target.projectId }?.sessions?.all { it.sessionId == target.sessionId } == true &&
                    state.needsYou.none { it.projectId == target.projectId }
                val ids = listOfNotNull(
                    target.sessionId,
                    target.claudeSessionId,
                    projectKey(target.projectId).takeIf { lastInProject && target.projectId.isNotEmpty() },
                )
                viewModelScope.launch { hiddenSessionsRepository.hide(ids, target.lastActivityMs) }
            }
        }
    }
}
