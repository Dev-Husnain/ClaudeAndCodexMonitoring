package com.claude.codex.ai.monitoring.data.network

import com.claude.codex.ai.monitoring.protocol.Message
import com.claude.codex.ai.monitoring.protocol.ProtocolCodec
import com.claude.codex.ai.monitoring.protocol.ProtocolConstants
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID

class HeartbeatTimeoutException : IOException("No frame from the desktop agent within the heartbeat window")

/** The desktop could not prove it holds the pinned key: possibly an impersonator (spec 6.3 step 2). */
class DesktopIdentityException : IOException("Desktop signature did not verify against the pinned key")

/** A message to send, with the envelope id the desktop echoes back in `ack`. */
data class OutgoingMessage(val message: Message, val id: String = UUID.randomUUID().toString())

/** One WebSocket connection to the desktop agent. */
class AgentSocketDataSource(
    private val client: HttpClient,
    private val codec: ProtocolCodec,
) {
    /**
     * Connects to [url], sends [hello], answers the desktop's challenge with [authenticate] and
     * only then starts forwarding [outgoing] and the heartbeat. Emits every decoded message after
     * the handshake, or the desktop's refusal if the handshake fails. Completes when the socket
     * closes and throws on network failure, heartbeat timeout or [DesktopIdentityException].
     */
    fun connect(
        url: String,
        hello: Message.Hello,
        authenticate: suspend (Message.Challenge) -> Message.Auth,
        outgoing: ReceiveChannel<OutgoingMessage>,
    ): Flow<Message> = channelFlow {
        client.webSocket(urlString = url) {
            send(Frame.Text(codec.encode(hello)))
            when (val first = receiveMessage() ?: return@webSocket) {
                is Message.Challenge -> send(Frame.Text(codec.encode(authenticate(first))))
                else -> {
                    // Refused before the handshake (NOT_PAIRED, RATE_LIMITED, …).
                    this@channelFlow.send(first)
                    return@webSocket
                }
            }
            coroutineScope {
                val writer = launch {
                    for (out in outgoing) send(Frame.Text(codec.encode(out.message, id = out.id)))
                }
                val heartbeat = launch {
                    while (isActive) {
                        delay(ProtocolConstants.HEARTBEAT_INTERVAL_MS)
                        send(Frame.Text(codec.encode(Message.Ping)))
                    }
                }
                try {
                    while (true) {
                        val message = receiveMessage() ?: break
                        this@channelFlow.send(message)
                    }
                } finally {
                    writer.cancel()
                    heartbeat.cancel()
                }
            }
        }
    }

    /** Next decoded message; null when the socket closes. Undecodable frames are skipped, not fatal. */
    private suspend fun DefaultClientWebSocketSession.receiveMessage(): Message? {
        while (true) {
            val result = withTimeoutOrNull(ProtocolConstants.CONNECTION_TIMEOUT_MS) { incoming.receiveCatching() }
                ?: throw HeartbeatTimeoutException()
            val frame = result.getOrNull() ?: return null
            if (frame is Frame.Text) codec.decode(frame.readText()).onSuccess { return it.message }
        }
    }
}
