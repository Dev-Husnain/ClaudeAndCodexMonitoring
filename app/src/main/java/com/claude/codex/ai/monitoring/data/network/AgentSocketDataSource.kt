package com.claude.codex.ai.monitoring.data.network

import com.claude.codex.ai.monitoring.protocol.Message
import com.claude.codex.ai.monitoring.protocol.ProtocolCodec
import com.claude.codex.ai.monitoring.protocol.ProtocolConstants
import io.ktor.client.HttpClient
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

class HeartbeatTimeoutException : IOException("No frame from the desktop agent within the heartbeat window")

/** One WebSocket connection to the desktop agent. */
class AgentSocketDataSource(
    private val client: HttpClient,
    private val codec: ProtocolCodec,
) {
    /**
     * Connects to [url], sends [hello] first, then forwards [outgoing] and a heartbeat ping.
     * Emits every decoded inbound message. Completes when the server closes the socket and
     * throws on network failure or when nothing arrives within the heartbeat window.
     */
    fun connect(url: String, hello: Message, outgoing: ReceiveChannel<Message>): Flow<Message> = channelFlow {
        client.webSocket(urlString = url) {
            send(Frame.Text(codec.encode(hello)))
            coroutineScope {
                val writer = launch {
                    for (message in outgoing) send(Frame.Text(codec.encode(message)))
                }
                val heartbeat = launch {
                    while (isActive) {
                        delay(ProtocolConstants.HEARTBEAT_INTERVAL_MS)
                        send(Frame.Text(codec.encode(Message.Ping)))
                    }
                }
                try {
                    while (true) {
                        val result = withTimeoutOrNull(ProtocolConstants.CONNECTION_TIMEOUT_MS) {
                            incoming.receiveCatching()
                        } ?: throw HeartbeatTimeoutException()
                        val frame = result.getOrNull() ?: break
                        if (frame is Frame.Text) {
                            // Undecodable frames (e.g. a newer protocol) are skipped, not fatal.
                            codec.decode(frame.readText()).onSuccess { this@channelFlow.send(it.message) }
                        }
                    }
                } finally {
                    writer.cancel()
                    heartbeat.cancel()
                }
            }
        }
    }
}
