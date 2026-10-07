package com.claude.codex.ai.monitoring.desktop.wrapper

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText

/**
 * Opens a terminal window on this computer running `agentmon --session <id> claude [args]`, so the phone can
 * start Claude when no terminal is open. The command goes into a small launch script, started with Windows'
 * `start`: a new console that Windows shows in its default terminal app. Only checked ids and fixed flags reach
 * the script, never text from the phone, and the environment is cleaned of Claude Code's own session variables
 * (an agent started from a Claude session would otherwise pass them on, and Claude would stop saving transcripts).
 */
class TerminalLauncher(
    private val scriptDir: Path,
    private val agentmon: () -> Path? = ::findAgentmon,
    private val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true),
    private val run: (command: List<String>, environment: Map<String, String>) -> Unit = ::startDetached,
) {
    /** Starts the terminal; returns null on success, else why it could not. */
    fun launch(workingDir: Path, sessionId: String, claudeArgs: List<String>, title: String): String? {
        if (!isWindows) return "Starting a terminal from the phone is available on Windows only"
        if (!WrapperHub.isValidId(sessionId) || claudeArgs.any { !SAFE_ARG.matches(it) }) return "Invalid request"
        if (!Files.isDirectory(workingDir)) return "The project folder no longer exists"
        val wrapper = agentmon() ?: return "The agentmon wrapper was not found on this computer. Install it and try again"
        return runCatching {
            scriptDir.createDirectories()
            removeOldScripts()
            val script = scriptDir.resolve("start-$sessionId.cmd")
            script.writeText(script(workingDir, wrapper, sessionId, claudeArgs, title))
            val environment = System.getenv().filterKeys { name ->
                !name.startsWith("CLAUDE", ignoreCase = true) && !name.startsWith("AGENTMON", ignoreCase = true)
            }
            run(listOf("cmd.exe", "/c", "start", "", script.toString()), environment)
            null
        }.getOrElse { "Could not open a terminal: ${it.message.orEmpty().take(200)}" }
    }

    private fun removeOldScripts() {
        val cutoff = System.currentTimeMillis() - SCRIPT_TTL_MS
        scriptDir.listDirectoryEntries("start-*.cmd")
            .filter { runCatching { it.getLastModifiedTime().toMillis() < cutoff }.getOrDefault(false) }
            .forEach { runCatching { Files.deleteIfExists(it) } }
    }

    companion object {
        /** Claude flags and conversation ids: letters, digits and dashes only. */
        private val SAFE_ARG = Regex("[A-Za-z0-9-]{1,64}")
        private const val SCRIPT_TTL_MS = 24 * 60 * 60_000L

        /** The launch script; [title] is reduced to safe characters, everything else was checked by the caller. */
        internal fun script(workingDir: Path, wrapper: Path, sessionId: String, claudeArgs: List<String>, title: String): String {
            val safeTitle = title.filter { it.isLetterOrDigit() || it in " -_." }.trim().take(60).ifEmpty { "Claude" }
            val call = if (wrapper.toString().endsWith(".bat", ignoreCase = true) || wrapper.toString().endsWith(".cmd", ignoreCase = true)) "call " else ""
            return listOf(
                "@echo off",
                "title $safeTitle (AgentMon)",
                "cd /d \"$workingDir\"",
                "$call\"$wrapper\" --session $sessionId claude${claudeArgs.joinToString("") { " $it" }}",
            ).joinToString("\r\n", postfix = "\r\n")
        }

        /**
         * The wrapper to start: next to a packaged `AgentMon.exe` (release layout `AgentMon\` + `cli\`), the copy
         * installed by `:cli:installWrapper`, or `agentmon` on PATH.
         */
        fun findAgentmon(): Path? {
            val candidates = buildList {
                System.getProperty("jpackage.app-path")?.let { runCatching { Path.of(it) }.getOrNull() }
                    ?.parent?.parent?.let { add(it.resolve("cli").resolve("agentmon.exe")) }
                System.getenv("LOCALAPPDATA")?.let { add(Path.of(it, "AgentMon", "cli", "bin", "agentmon.bat")) }
                System.getenv("PATH").orEmpty().split(java.io.File.pathSeparatorChar).filter { it.isNotBlank() }.forEach { dir ->
                    listOf("agentmon.exe", "agentmon.bat", "agentmon.cmd").forEach { name ->
                        runCatching { Path.of(dir.trim('"'), name) }.getOrNull()?.let(::add)
                    }
                }
            }
            return candidates.firstOrNull { it.exists() && it.isRegularFile() }
        }

        private fun startDetached(command: List<String>, environment: Map<String, String>) {
            val builder = ProcessBuilder(command)
            builder.environment().clear()
            builder.environment().putAll(environment)
            builder.start()
        }
    }
}
