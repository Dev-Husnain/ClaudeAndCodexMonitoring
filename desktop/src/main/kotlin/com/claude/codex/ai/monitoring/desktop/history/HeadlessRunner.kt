package com.claude.codex.ai.monitoring.desktop.history

import com.claude.codex.ai.monitoring.desktop.session.SessionRegistry
import com.claude.codex.ai.monitoring.platform.ClaudeCommand
import com.claude.codex.ai.monitoring.protocol.EventKind
import com.claude.codex.ai.monitoring.protocol.ProtocolConstants
import com.claude.codex.ai.monitoring.protocol.SessionState
import com.claude.codex.ai.monitoring.protocol.TimelineEventDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs `claude -p --resume <id>` for conversations continued from the phone (spec phase 6). It uses the
 * owner's normal Claude login, like typing in a terminal. The prompt goes in through stdin, never on the
 * command line, so phone text can never be read as shell syntax. The run's hooks report progress as
 * usual; its own output is only checked for an error result and is never stored.
 */
class HeadlessRunner(
    private val registry: SessionRegistry,
    private val scope: CoroutineScope,
    private val resolveCommand: (List<String>) -> List<String>? = { ClaudeCommand.resolve(it) },
    private val launch: (command: List<String>, workingDir: Path) -> Process = { command, dir ->
        ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start()
    },
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private class Run(val process: Process, val claudeSessionId: String) {
        @Volatile
        var stopped = false
    }

    /** Running resumes by AgentMon session id. */
    private val runs = ConcurrentHashMap<String, Run>()

    /** Claude's session id → the AgentMon session it is shown as (differs for a wrapper terminal's conversation). */
    private val aliases = ConcurrentHashMap<String, String>()

    private val json = Json { ignoreUnknownKeys = true }

    fun isRunning(sessionId: String): Boolean = runs[sessionId]?.process?.isAlive == true

    /** The AgentMon session a resumed run's hooks belong to, when it is not Claude's own id. */
    fun sessionIdFor(claudeSessionId: String): String? = aliases[claudeSessionId]

    /** Starts the run; returns why it could not, or null. */
    fun start(sessionId: String, claudeSessionId: String, workingDir: Path, prompt: String): String? {
        if (isRunning(sessionId)) return "Claude is already working on this conversation"
        val command = resolveCommand(listOf("-p", "--resume", claudeSessionId, "--output-format", "stream-json", "--verbose"))
            ?: return "Claude Code was not found on this computer"
        val process = runCatching { launch(command, workingDir) }.getOrElse { return "Claude could not be started" }
        runs[sessionId] = Run(process, claudeSessionId)
        if (claudeSessionId != sessionId) aliases[claudeSessionId] = sessionId
        runCatching { process.outputStream.use { it.write(prompt.toByteArray(Charsets.UTF_8)) } }
        scope.launch(Dispatchers.IO) {
            val resultError = errorOf(process)
            val code = runCatching { process.waitFor() }.getOrDefault(-1)
            val stopped = runs[sessionId]?.takeIf { it.process === process }?.stopped == true
            val error = when {
                stopped -> null // Stop from the phone, not a failure.
                resultError != null -> resultError
                code != 0 -> "Claude exited with code $code"
                else -> null
            }
            finished(sessionId, process, error)
        }
        return null
    }

    /** Stops a running resume (Stop from the phone), including what Claude started. */
    fun stop(sessionId: String): Boolean {
        val run = runs[sessionId] ?: return false
        run.stopped = true
        // `cmd /c claude` on Windows: end Claude itself, not only the shell that started it.
        runCatching { run.process.toHandle().descendants().forEach { it.destroy() } }
        run.process.destroy()
        return true
    }

    /** Reads the stream to its end; the error text of a failed `result`, if any. */
    private fun errorOf(process: Process): String? {
        var error: String? = null
        process.inputStream.bufferedReader().useLines { lines ->
            lines.filter { it.contains("\"type\":\"result\"") }.forEach { line ->
                val result = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return@forEach
                val failed = (result["is_error"] as? JsonPrimitive)?.content == "true"
                error = if (failed) {
                    ((result["result"] as? JsonPrimitive)?.content ?: (result["subtype"] as? JsonPrimitive)?.content ?: "Claude reported an error")
                        .take(ProtocolConstants.MAX_TEXT_CHARS)
                } else {
                    null
                }
            }
        }
        return error
    }

    private fun finished(sessionId: String, process: Process, error: String?) {
        val run = runs[sessionId]
        if (run?.process === process) {
            runs.remove(sessionId)
            aliases.remove(run.claudeSessionId, sessionId)
        }
        val now = clock()
        registry.mutateSession(sessionId) { current ->
            current?.copy(
                state = if (error != null) SessionState.ERROR else SessionState.ENDED,
                awaiting = null,
                errorInfo = error ?: current.errorInfo,
                lastEventAt = now,
            )
        }
        if (error != null) registry.addEvent(TimelineEventDto(newId(), sessionId, now, EventKind.ERROR, "Resuming failed", error))
    }
}
