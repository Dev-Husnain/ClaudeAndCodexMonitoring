package com.claude.codex.ai.monitoring.cli

import com.claude.codex.ai.monitoring.protocol.ProtocolConstants
import com.claude.codex.ai.monitoring.protocol.WrapperMessage
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * The wrapper's connection to the desktop agent on 127.0.0.1. It never blocks Claude: when the agent
 * is not running, output is only kept in a small in-memory buffer and the link retries quietly. On
 * every (re)connect that buffer is replayed so the agent's terminal mirror catches up.
 */
class AgentLink(
    private val secret: String,
    private val hello: () -> WrapperMessage.Hello,
    /** Types into Claude; true when it was written. */
    private val onInput: (String) -> Boolean,
    private val url: String = "ws://${ProtocolConstants.LOOPBACK_HOST}:${ProtocolConstants.DEFAULT_PORT}${ProtocolConstants.PATH_WRAPPER}",
    private val replayChars: Int = REPLAY_CHARS,
) {
    private val client = HttpClient(CIO) { install(WebSockets) }
    private val lock = Any()
    private val replay = StringBuilder()
    private var current: Channel<WrapperMessage>? = null

    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            runCatching {
                client.webSocket(url, { header(ProtocolConstants.HEADER_SECRET, secret) }) {
                    val outgoing = Channel<WrapperMessage>(OUTGOING_CAPACITY)
                    val backlog = synchronized(lock) {
                        current = outgoing
                        replay.toString()
                    }
                    send(Frame.Text(WrapperMessage.encode(hello())))
                    if (backlog.isNotEmpty()) send(Frame.Text(WrapperMessage.encode(WrapperMessage.Output(backlog))))
                    val writer = launch { for (message in outgoing) send(Frame.Text(WrapperMessage.encode(message))) }
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val input = WrapperMessage.decode(frame.readText()) as? WrapperMessage.Input ?: continue
                            val typed = onInput(input.data)
                            input.id?.let { outgoing.trySend(WrapperMessage.InputAck(it, typed)) }
                        }
                    } finally {
                        synchronized(lock) { if (current === outgoing) current = null }
                        outgoing.close()
                        writer.cancel()
                    }
                }
            }
            delay(RETRY_MS)
        }
    }

    fun output(text: String) = synchronized(lock) {
        replay.append(text)
        if (replay.length > replayChars) {
            // Cut at a line start where possible, so the replay rarely begins inside an escape sequence.
            val cut = replay.length - replayChars
            val lineStart = replay.indexOf("\n", cut).takeIf { it in cut until replay.length }?.plus(1) ?: cut
            replay.delete(0, lineStart)
        }
        current?.trySend(WrapperMessage.Output(text))
    }

    fun send(message: WrapperMessage) = synchronized(lock) { current?.trySend(message) }

    /** Tells the agent Claude exited, waiting briefly so the frame goes out before the process ends. */
    suspend fun finish(code: Int) {
        send(WrapperMessage.Exit(code))
        delay(FLUSH_MS)
        client.close()
    }

    companion object {
        private const val OUTGOING_CAPACITY = 4_096
        private const val REPLAY_CHARS = 200_000
        private const val RETRY_MS = 3_000L
        private const val FLUSH_MS = 200L

        /** The secret the desktop agent created; null when the agent was never started on this computer. */
        fun readSecret(env: Map<String, String> = System.getenv()): String? {
            val dir = env["APPDATA"]?.let { Path.of(it, "AgentMon") } ?: Path.of(System.getProperty("user.home"), ".agentmon")
            val file = dir.resolve("hook-secret")
            return if (file.exists() && Files.isReadable(file)) file.readText().trim().takeIf { it.isNotEmpty() } else null
        }
    }
}
