package com.claude.codex.ai.monitoring.desktop.server

import com.claude.codex.ai.monitoring.protocol.Message
import com.claude.codex.ai.monitoring.protocol.ProtocolCodec
import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live authenticated connections per device, so a revoke or grant change takes effect at once (spec 6.4). */
class ConnectionHub(private val codec: ProtocolCodec) {

    private val sessions = HashMap<String, MutableSet<DefaultWebSocketSession>>()
    private val _online = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Device id to number of open connections. */
    val online: StateFlow<Map<String, Int>> = _online.asStateFlow()

    fun register(deviceId: String, session: DefaultWebSocketSession) = mutate {
        sessions.getOrPut(deviceId) { mutableSetOf() }.add(session)
    }

    fun unregister(deviceId: String, session: DefaultWebSocketSession) = mutate {
        sessions[deviceId]?.let { set ->
            set.remove(session)
            if (set.isEmpty()) sessions.remove(deviceId)
        }
    }

    /** Tells the phone it was revoked, then drops every connection of that device. */
    suspend fun revoke(deviceId: String) {
        snapshot(deviceId).forEach { session ->
            runCatching {
                session.send(Frame.Text(codec.encode(Message.Revoked)))
                session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Revoked"))
            }
        }
    }

    /** Closes connections so the phone reconnects and receives its updated grant. */
    suspend fun reconnect(deviceId: String) {
        snapshot(deviceId).forEach { session ->
            runCatching { session.close(CloseReason(CloseReason.Codes.GOING_AWAY, "Access changed")) }
        }
    }

    private fun snapshot(deviceId: String): List<DefaultWebSocketSession> =
        synchronized(sessions) { sessions[deviceId]?.toList().orEmpty() }

    private fun mutate(block: () -> Unit) {
        synchronized(sessions) {
            block()
            _online.value = sessions.mapValues { it.value.size }
        }
    }
}
