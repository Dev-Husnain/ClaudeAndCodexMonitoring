package com.claude.codex.ai.monitoring.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claude.codex.ai.monitoring.domain.repo.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    appVersion: String,
) : ViewModel() {

    private val _settingsUiState = MutableStateFlow(SettingsUiState(appVersion = appVersion))
    val settingsUiState: StateFlow<SettingsUiState> = _settingsUiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _settingsUiState.update { state ->
                    state.copy(
                        themeOptions = state.themeOptions.map { it.copy(selected = it.mode == settings.themeMode) },
                        hapticsEnabled = settings.hapticsEnabled,
                    )
                }
            }
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.OnThemeModeSelect -> viewModelScope.launch { settingsRepository.setThemeMode(event.mode) }
            is SettingsEvent.OnHapticsToggle -> viewModelScope.launch { settingsRepository.setHapticsEnabled(event.enabled) }
        }
    }
}
