package com.claude.codex.ai.monitoring.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claude.codex.ai.monitoring.core.utils.Clock
import com.claude.codex.ai.monitoring.core.utils.ticks
import com.claude.codex.ai.monitoring.domain.repo.AgentRepository
import com.claude.codex.ai.monitoring.domain.repo.PairingRepository
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
    clock: Clock,
) : ViewModel() {

    private val _homeUiState = MutableStateFlow(HomeUiState())
    val homeUiState: StateFlow<HomeUiState> = _homeUiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(observeSessionOverview(), pairingRepository.pairing, clock.ticks()) { overview, pairing, now ->
                overview.toHomeUiState(now, pairing?.computerName)
            }.collect { state -> _homeUiState.update { state.copy(awayBusy = it.awayBusy) } }
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
        }
    }
}
