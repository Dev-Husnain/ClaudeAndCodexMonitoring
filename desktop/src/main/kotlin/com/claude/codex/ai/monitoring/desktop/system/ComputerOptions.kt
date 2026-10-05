package com.claude.codex.ai.monitoring.desktop.system

import com.claude.codex.ai.monitoring.desktop.session.RegistryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** "This computer" options on the Overview screen: the phone access address, stay awake, start with Windows. */
class ComputerOptions(
    private val settings: DesktopSettings,
    private val keepAwake: KeepAwake,
    private val autoStart: AutoStart,
    private val scope: CoroutineScope,
    /** An address given on the command line or in `AGENTMON_PUBLIC_URL`; it wins over the saved one. */
    private val publicUrlOverride: PublicUrlOverride? = null,
) {
    /** Where an address that cannot be edited in the window comes from. */
    data class PublicUrlOverride(val url: String, val source: String)

    /** The tunnel address for pairing codes, or null when only USB pairing is possible. */
    val publicUrl: StateFlow<String?> =
        publicUrlOverride?.let { MutableStateFlow<String?>(it.url).asStateFlow() } ?: settings.publicUrl

    /** Set when the address comes from the command line or environment, naming where. */
    val publicUrlLockedBy: String? get() = publicUrlOverride?.source

    private val _addressError = MutableStateFlow<String?>(null)
    val addressError: StateFlow<String?> = _addressError.asStateFlow()

    /** Saves the address typed on the Overview screen. Returns false (with [addressError]) when it is not valid. */
    fun setPublicUrl(input: String): Boolean {
        if (publicUrlOverride != null) return false
        return when (val parsed = PublicAddress.parse(input)) {
            is PublicAddress.Parsed.Valid -> true.also { settings.setPublicUrl(parsed.url); _addressError.value = null }
            PublicAddress.Parsed.Cleared -> true.also { settings.setPublicUrl(null); _addressError.value = null }
            is PublicAddress.Parsed.Invalid -> false.also { _addressError.value = parsed.reason }
        }
    }

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
