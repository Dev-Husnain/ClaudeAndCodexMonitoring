package com.claude.codex.ai.monitoring.desktop.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Counts live phone connections per device id, for the desktop UI. */
class ConnectionTracker {
    private val _connected = MutableStateFlow<Map<String, Int>>(emptyMap())
    val connected: StateFlow<Map<String, Int>> = _connected.asStateFlow()

    fun opened(deviceId: String) = _connected.update { it + (deviceId to (it[deviceId] ?: 0) + 1) }

    fun closed(deviceId: String) = _connected.update { current ->
        val remaining = (current[deviceId] ?: 1) - 1
        if (remaining <= 0) current - deviceId else current + (deviceId to remaining)
    }
}
