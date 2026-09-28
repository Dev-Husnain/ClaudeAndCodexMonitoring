package com.claude.codex.ai.monitoring.desktop.server

import com.claude.codex.ai.monitoring.desktop.session.SessionRegistry
import com.claude.codex.ai.monitoring.protocol.ErrorCode
import com.claude.codex.ai.monitoring.protocol.Message
import com.claude.codex.ai.monitoring.protocol.ProtocolCodec
import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Serves one phone connection: waits for `hello`, sends the `ready` snapshot, then streams
 * registry updates and answers requests. Phase 1: no authentication yet (added in phase 3,
 * spec 6.3); the server is reachable only on 127.0.0.1.
 */
class ClientHandler(
    private val registry: SessionRegistry,
    private val codec: ProtocolCodec,
    private val connections: ConnectionTracker,
    private val helloTimeoutMs: Long = HELLO_TIMEOUT_MS,
) {
    suspend fun handle(session: DefaultWebSocketSession) = with(session) {
        val hello = withTimeoutOrNull(helloTimeoutMs) { receiveMessage() } as? Message.Hello
        if (hello == null) {
            send(Frame.Text(codec.encode(Message.Error(ErrorCode.BAD_REQUEST, "Expected hello"))))
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Expected hello"))
            return
        }
        connections.opened(hello.deviceId)
        try {
            send(Frame.Text(codec.encode(registry.snapshot())))
            coroutineScope {
                val forwarder = launch {
                    registry.updates.collect { update ->
                        send(Frame.Text(codec.encode(update, projectId = update.projectIdOrNull())))
                    }
                }
                while (true) {
                    val message = receiveMessage() ?: break
                    respond(message)?.let { send(Frame.Text(codec.encode(it))) }
                }
                forwarder.cancel()
            }
        } finally {
            connections.closed(hello.deviceId)
        }
    }

    private fun respond(message: Message): Message? = when (message) {
        Message.Ping -> Message.Pong
        Message.SessionList -> registry.snapshot()
        is Message.SessionHistory -> registry.history(message.sessionId, message.beforeTs)
        // Phase 1 routes every project to every client; the allow-list arrives with pairing (phase 3).
        is Message.Subscribe -> null
        is Message.SendInput, is Message.QuickActionRequest, is Message.TerminalAttach, Message.TerminalDetach ->
            Message.Error(ErrorCode.SESSION_NOT_CONTROLLABLE, "Input is not available yet")
        else -> Message.Error(ErrorCode.BAD_REQUEST, "Unexpected message")
    }

    /** Next decoded message, skipping undecodable frames; null when the socket closes. */
    private suspend fun DefaultWebSocketSession.receiveMessage(): Message? {
        while (true) {
            val frame = incoming.receiveCatching().getOrNull() ?: return null
            if (frame !is Frame.Text) continue
            codec.decode(frame.readText()).onSuccess { return it.message }
        }
    }

    private fun Message.projectIdOrNull(): String? = when (this) {
        is Message.SessionUpdate -> session.projectId
        is Message.SessionEvent -> registry.projectOf(sessionId)
        else -> null
    }

    private companion object {
        const val HELLO_TIMEOUT_MS = 10_000L
    }
}
