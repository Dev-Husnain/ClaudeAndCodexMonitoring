package com.claude.codex.ai.monitoring.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.domain.models.ServerUrlError
import com.claude.codex.ai.monitoring.domain.repo.SettingsRepository
import com.claude.codex.ai.monitoring.domain.usecase.UpdateServerUrlUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val updateServerUrl: UpdateServerUrlUseCase,
    appVersion: String,
) : ViewModel() {

    private val _settingsUiState = MutableStateFlow(SettingsUiState(appVersion = appVersion))
    val settingsUiState: StateFlow<SettingsUiState> = _settingsUiState.asStateFlow()

    init {
        viewModelScope.launch {
            // The address field is seeded once; after that it belongs to the user while editing.
            val initial = settingsRepository.settings.first()
            _settingsUiState.update { it.copy(serverUrlInput = initial.serverUrl) }
            settingsRepository.settings.collect { settings ->
                _settingsUiState.update { state ->
                    state.copy(
                        themeOptions = state.themeOptions.map { it.copy(selected = it.mode == settings.themeMode) },
                        hapticsEnabled = settings.hapticsEnabled,
                        deviceId = settings.deviceId,
                    )
                }
            }
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.OnThemeModeSelect -> viewModelScope.launch { settingsRepository.setThemeMode(event.mode) }
            is SettingsEvent.OnHapticsToggle -> viewModelScope.launch { settingsRepository.setHapticsEnabled(event.enabled) }
            is SettingsEvent.OnServerUrlChange -> _settingsUiState.update {
                it.copy(serverUrlInput = event.value, serverUrlError = null, serverUrlSaved = false)
            }
            SettingsEvent.OnServerUrlSave -> saveServerUrl()
        }
    }

    private fun saveServerUrl() {
        viewModelScope.launch {
            updateServerUrl(_settingsUiState.value.serverUrlInput)
                .onSuccess { saved ->
                    _settingsUiState.update { it.copy(serverUrlInput = saved, serverUrlError = null, serverUrlSaved = true) }
                }
                .onFailure { error ->
                    val message = when (error) {
                        is ServerUrlError.Insecure -> R.string.settings_server_url_insecure
                        else -> R.string.settings_server_url_invalid
                    }
                    _settingsUiState.update { it.copy(serverUrlError = message, serverUrlSaved = false) }
                }
        }
    }
}
