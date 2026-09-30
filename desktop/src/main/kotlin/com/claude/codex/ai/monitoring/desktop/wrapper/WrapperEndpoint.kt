package com.claude.codex.ai.monitoring.desktop.wrapper

import com.claude.codex.ai.monitoring.desktop.devices.AuditCategory
import com.claude.codex.ai.monitoring.desktop.devices.AuditLog
import com.claude.codex.ai.monitoring.protocol.AgentCrypto
import com.claude.codex.ai.monitoring.protocol.WrapperMessage
import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * `ws://127.0.0.1:8787/wrapper`: where `agentmon claude` connects. Same rules as `/hook`: the hook
 * secret is required and anything that came through the Cloudflare tunnel is refused, so only
 * programs of this user on this computer can attach a terminal.
 */
class WrapperEndpoint(
    private val secret: String,
    private val hub: WrapperHub,
    private val audit: AuditLog,
    private val helloTimeoutMs: Long = HELLO_TIMEOUT_MS,
) {
    suspend fun handle(session: DefaultWebSocketSession, secretHeader: String?, viaTunnel: Boolean) = with(session) {
        if (viaTunnel || secretHeader == null || !AgentCrypto.secretsEqual(secretHeader, secret)) {
            audit.record(AuditCategory.SERVER, if (viaTunnel) "Terminal link through the tunnel refused" else "Terminal link with a wrong secret refused")
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Forbidden"))
            return
        }
        val hello = withTimeoutOrNull(helloTimeoutMs) { receive() } as? WrapperMessage.Hello
        if (hello == null || !WrapperHub.isValidId(hello.wrapperId)) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Expected hello"))
            return
        }
        val outgoing = hub.connect(hello)
        var exited = false
        try {
            coroutineScope {
                val writer = launch {
                    for (message in outgoing) send(Frame.Text(WrapperMessage.encode(message)))
                }
                while (true) {
                    when (val message = receive() ?: break) {
                        is WrapperMessage.Exit -> {
                            exited = true
                            break
                        }
                        else -> hub.onFrame(hello.wrapperId, message)
                    }
                }
                writer.cancel()
            }
        } finally {
            hub.disconnect(hello.wrapperId, outgoing, claudeExited = exited)
        }
    }

    /** Next decodable frame; null when the socket closes. */
    private suspend fun DefaultWebSocketSession.receive(): WrapperMessage? {
        while (true) {
            val frame = incoming.receiveCatching().getOrNull() ?: return null
            if (frame is Frame.Text) WrapperMessage.decode(frame.readText())?.let { return it }
        }
    }

    private companion object {
        const val HELLO_TIMEOUT_MS = 5_000L
    }
}
