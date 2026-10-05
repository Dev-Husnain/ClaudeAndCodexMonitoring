package com.claude.codex.ai.monitoring.desktop.history

import com.claude.codex.ai.monitoring.protocol.PastSessionDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension

/**
 * Reads the conversations Claude Code saved for a project:
 * `~/.claude/projects/<cwd with every non-alphanumeric character replaced by "-">/<session-id>.jsonl`
 * (or under `CLAUDE_CONFIG_DIR`). The line format is internal to Claude Code and changes between
 * versions, so only a title is taken from it, defensively, and nothing else is sent or stored.
 */
class TranscriptStore(
    private val configDir: Path = defaultConfigDir(),
    private val maxBytesPerFile: Long = MAX_BYTES_PER_FILE,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Saved conversations whose working directory is [projectPath] or inside it, newest first. */
    fun list(projectPath: Path, limit: Int = DEFAULT_LIMIT): List<PastSessionDto> =
        transcriptsOf(projectPath)
            .sortedByDescending { it.getLastModifiedTime().toMillis() }
            .take(limit)
            .mapNotNull { file -> read(file) }

    /** The transcript of [claudeSessionId] in [projectPath]'s folders, if Claude saved one. */
    fun find(projectPath: Path, claudeSessionId: String): Path? =
        transcriptsOf(projectPath).firstOrNull { it.nameWithoutExtension == claudeSessionId }

    /** The folder Claude ran in for that conversation (may be below the project), from its first lines. */
    fun workingDirectory(transcript: Path): Path? = runCatching {
        Files.newBufferedReader(transcript).useLines { lines ->
            lines.take(HEAD_LINES).firstNotNullOfOrNull { line ->
                if (!line.contains("\"cwd\"")) return@firstNotNullOfOrNull null
                (parse(line)?.get("cwd") as? JsonPrimitive)?.content?.let { Path.of(it) }
            }
        }
    }.getOrNull()

    private fun transcriptsOf(projectPath: Path): List<Path> {
        val root = configDir.resolve("projects")
        if (!root.isDirectory()) return emptyList()
        val project = projectPath.toAbsolutePath().normalize()
        val prefix = folderName(project)
        return root.listDirectoryEntries()
            // Case-insensitive: Windows paths are, and the agent stores project paths lowercased there.
            .filter { it.isDirectory() && (it.fileName.toString().equals(prefix, ignoreCase = true) || it.fileName.toString().startsWith("$prefix-", ignoreCase = true)) }
            .filter { dir ->
                // "app-old" and a subfolder of "app" both start with "app-": confirm with a recorded working directory.
                dir.fileName.toString().equals(prefix, ignoreCase = true) || dir.listDirectoryEntries("*.jsonl").firstNotNullOfOrNull(::workingDirectory)
                    ?.toAbsolutePath()?.normalize()?.startsWith(project) == true
            }
            .flatMap { dir -> dir.listDirectoryEntries("*.jsonl") }
            .filter { it.extension == "jsonl" && UUID.matches(it.nameWithoutExtension) }
    }

    /**
     * The title of a live conversation's transcript, as Claude Code's hooks report its path. Only files in
     * Claude's own projects folder are read.
     */
    fun titleOf(transcriptPath: String): String? = runCatching {
        val root = configDir.resolve("projects").toAbsolutePath().normalize()
        val file = Path.of(transcriptPath).toAbsolutePath().normalize()
        file.takeIf { it.startsWith(root) && it.extension == "jsonl" && Files.isRegularFile(it) }?.let(::read)?.title
    }.getOrNull()

    private fun read(file: Path): PastSessionDto? = runCatching {
        var name: String? = null
        var aiTitle: String? = null
        var firstPrompt: String? = null
        var bytes = 0L
        Files.newBufferedReader(file).useLines { lines ->
            for (line in lines) {
                bytes += line.length + 1
                if (bytes > maxBytesPerFile) break
                when {
                    line.contains("\"agent-name\"") -> (parse(line)?.get("agentName") as? JsonPrimitive)?.content?.let { name = it }
                    line.contains("\"ai-title\"") -> (parse(line)?.get("aiTitle") as? JsonPrimitive)?.content?.let { aiTitle = it }
                    firstPrompt == null && line.contains("\"type\":\"user\"") -> firstPrompt = parse(line)?.userText()
                }
            }
        }
        val title = (name ?: aiTitle ?: firstPrompt)?.oneLine()?.takeIf { it.isNotBlank() } ?: return null
        PastSessionDto(file.nameWithoutExtension, title.take(TITLE_CHARS), file.getLastModifiedTime().toMillis())
    }.getOrNull()

    /** A typed prompt: plain string content that is not a command or system wrapper (`<command-name>` …). */
    private fun JsonObject.userText(): String? {
        val content = (get("message") as? JsonObject)?.get("content") as? JsonPrimitive ?: return null
        return content.content.trim().takeIf { it.isNotEmpty() && !it.startsWith("<") }
    }

    private fun parse(line: String): JsonObject? = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull()

    private fun String.oneLine(): String = lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()

    companion object {
        private const val DEFAULT_LIMIT = 30
        private const val TITLE_CHARS = 120
        private const val HEAD_LINES = 200
        private const val MAX_BYTES_PER_FILE = 8L * 1024 * 1024
        val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        /** How Claude Code names a project's folder: every non-alphanumeric character becomes "-". */
        fun folderName(projectPath: Path): String = projectPath.toAbsolutePath().normalize().toString().replace(Regex("[^A-Za-z0-9]"), "-")

        fun defaultConfigDir(): Path = System.getenv("CLAUDE_CONFIG_DIR")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
            ?: Path.of(System.getProperty("user.home"), ".claude")
    }
}
