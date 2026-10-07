package com.claude.codex.ai.monitoring.cli

import com.claude.codex.ai.monitoring.platform.ClaudeCommand
import com.claude.codex.ai.monitoring.protocol.ProtocolConstants
import com.claude.codex.ai.monitoring.protocol.WrapperMessage
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

/**
 * `agentmon claude`: runs Claude Code in a pseudo-terminal (ConPTY on Windows) and sits between it and
 * the real console. Keystrokes pass straight through, the screen is shown as usual, and the same
 * output is streamed to the local AgentMon agent, which can type into it for the phone.
 * Claude gets `AGENTMON_WRAPPER_ID` in its environment; its hooks send it back so the agent knows
 * which session this terminal belongs to.
 */
class WrapperSession(private val args: List<String>, private val sessionId: String? = null) {

    fun run(): Int {
        val command = ClaudeCommand.resolve(args)
        if (command == null) {
            System.err.println(
                "agentmon: could not find `claude` on PATH. Install Claude Code, or set AGENTMON_CLAUDE to its full path " +
                    "(a .cmd path must not contain spaces).",
            )
            return 127
        }
        val secret = AgentLink.readSecret()
        if (secret == null) {
            System.err.println("agentmon: the AgentMon desktop agent has not been set up on this computer; starting Claude without the phone link.")
        }

        val terminal = TerminalBuilder.builder()
            .system(true)
            .signalHandler(Terminal.SignalHandler.SIG_IGN)
            .build()
        val columns = terminal.width.takeIf { it > 0 } ?: DEFAULT_COLUMNS
        val rows = terminal.height.takeIf { it > 0 } ?: DEFAULT_ROWS
        val wrapperId = sessionId ?: UUID.randomUUID().toString()
        val cwd = System.getProperty("user.dir")

        val process: PtyProcess = PtyProcessBuilder(command.toTypedArray())
            .setEnvironment(System.getenv() + (ProtocolConstants.ENV_WRAPPER_ID to wrapperId))
            .setDirectory(cwd)
            .setInitialColumns(columns)
            .setInitialRows(rows)
            .setConsole(false)
            .setUseWinConPty(true)
            .start()
        val toClaude: OutputStream = process.outputStream
        fun typeIntoClaude(bytes: ByteArray) = synchronized(toClaude) {
            runCatching {
                toClaude.write(bytes)
                toClaude.flush()
            }
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var size = columns to rows
        val link = secret?.let {
            AgentLink(
                secret = it,
                hello = { WrapperMessage.Hello(wrapperId, cwd, size.first, size.second, listOf(WrapperMessage.FEATURE_INPUT_ACK)) },
                onInput = { data -> typeIntoClaude(data.toByteArray(Charsets.UTF_8)).isSuccess },
            ).also { link -> link.start(scope) }
        }

        val saved = terminal.enterRawMode()
        terminal.handle(Terminal.Signal.WINCH) {
            size = terminal.width to terminal.height
            runCatching { process.winSize = WinSize(size.first, size.second) }
            link?.send(WrapperMessage.Resize(size.first, size.second))
        }

        // Claude → console (and the agent).
        val screen = terminal.writer()
        val output = thread(name = "agentmon-output") {
            val decoder = Utf8Decoder()
            val buffer = ByteArray(BUFFER_BYTES)
            val input = process.inputStream
            while (true) {
                val count = runCatching { input.read(buffer) }.getOrDefault(-1)
                if (count < 0) break
                if (count == 0) continue
                val text = decoder.decode(buffer, count)
                screen.write(text)
                screen.flush()
                link?.output(text)
            }
        }

        // Keyboard → Claude.
        thread(name = "agentmon-input", isDaemon = true) {
            val keys = terminal.input()
            val buffer = ByteArray(BUFFER_BYTES)
            while (process.isAlive) {
                val count = runCatching { keys.read(buffer) }.getOrDefault(-1)
                if (count < 0) break
                if (count > 0) typeIntoClaude(buffer.copyOf(count))
            }
        }

        val code = process.waitFor()
        output.join(OUTPUT_DRAIN_MS)
        runBlocking { link?.finish(code) }
        scope.cancel()
        terminal.attributes = saved
        runCatching { terminal.close() }
        return code
    }

    private companion object {
        const val DEFAULT_COLUMNS = 120
        const val DEFAULT_ROWS = 30
        const val BUFFER_BYTES = 8 * 1024
        const val OUTPUT_DRAIN_MS = 2_000L
    }
}
