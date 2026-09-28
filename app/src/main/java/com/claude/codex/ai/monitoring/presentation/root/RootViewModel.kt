package com.claude.codex.ai.monitoring.presentation.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claude.codex.ai.monitoring.domain.repo.PairingRepository
import com.claude.codex.ai.monitoring.domain.repo.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** App-wide concerns only: which theme to apply and whether this phone is paired. */
class RootViewModel(
    settingsRepository: SettingsRepository,
    pairingRepository: PairingRepository,
) : ViewModel() {

    private val _rootUiState = MutableStateFlow(RootUiState())
    val rootUiState: StateFlow<RootUiState> = _rootUiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _rootUiState.update { it.copy(themeMode = settings.themeMode) }
            }
        }
        viewModelScope.launch {
            pairingRepository.pairing.collect { pairing ->
                _rootUiState.update { it.copy(pairState = if (pairing != null) PairState.PAIRED else PairState.UNPAIRED) }
            }
        }
    }
}
