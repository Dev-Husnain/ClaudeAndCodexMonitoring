package com.claude.codex.ai.monitoring.desktop.system

import com.claude.codex.ai.monitoring.desktop.session.RegistryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** "This computer" options on the Overview screen: stay awake, and start with Windows. */
class ComputerOptions(
    private val settings: DesktopSettings,
    private val keepAwake: KeepAwake,
    private val autoStart: AutoStart,
    private val scope: CoroutineScope,
) {
    val keepAwakeEnabled: StateFlow<Boolean> = settings.keepAwake

    private val _startWithWindows = MutableStateFlow(runCatching { autoStart.isEnabled() }.getOrDefault(false))
    val startWithWindows: StateFlow<Boolean> = _startWithWindows.asStateFlow()

    val autoStartSupported: Boolean get() = autoStart.supported

    /** Why the last change failed, shown under the switches until the next change. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Keeps the computer awake according to the sessions, Away mode and the setting. */
    fun start(registry: StateFlow<RegistryState>, awayMode: StateFlow<Boolean>) {
        scope.launch {
            combine(registry, awayMode, settings.keepAwake) { state, away, enabled -> Triple(state, away, enabled) }
                .collect { (state, away, enabled) -> keepAwake.update(enabled, away, state.sessions) }
        }
    }

    fun setKeepAwake(enabled: Boolean) {
        _error.value = null
        settings.setKeepAwake(enabled)
    }

    fun setStartWithWindows(enabled: Boolean) {
        _error.value = null
        scope.launch(Dispatchers.IO) {
            runCatching { if (enabled) autoStart.enable() else autoStart.disable() }
                .onFailure { _error.value = it.message ?: "Could not change starting with Windows" }
            _startWithWindows.value = runCatching { autoStart.isEnabled() }.getOrDefault(false)
        }
    }
}
