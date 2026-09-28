package com.claude.codex.ai.monitoring.desktop.hooks

import com.claude.codex.ai.monitoring.desktop.storage.AppStorage
import com.claude.codex.ai.monitoring.protocol.AgentCrypto
import com.claude.codex.ai.monitoring.protocol.ProtocolConstants
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Why an install or uninstall could not be done safely. */
class HookInstallException(message: String) : Exception(message)

/**
 * Adds AgentMon's HTTP hooks to a project's `.claude/settings.local.json` (spec 7.2), and
 * removes them again. It merges instead of overwriting: the user's own hooks and every other
 * setting are kept as they are, and our entries are recognised by their URL, so installing twice
 * is harmless. `settings.local.json` is not committed; if the project's .gitignore does not
 * already cover it, the line is added so the hook secret never lands in git.
 */
class HookInstaller(
    val secret: String,
    private val hookUrl: String = "http://${ProtocolConstants.LOOPBACK_HOST}:${ProtocolConstants.DEFAULT_PORT}${ProtocolConstants.PATH_HOOK}",
) {
    private val json = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun settingsFile(projectDir: Path): Path = projectDir.resolve(".claude").resolve("settings.local.json")

    fun isInstalled(projectDir: Path): Boolean {
        val file = settingsFile(projectDir)
        if (!file.exists()) return false
        val hooks = runCatching { json.parseToJsonElement(file.readText()).jsonObject["hooks"] as? JsonObject }.getOrNull() ?: return false
        return EVENTS.all { event -> (hooks[event] as? JsonArray)?.any { it.isOurs() } == true }
    }

    fun install(projectDir: Path) {
        val file = settingsFile(projectDir)
        val root = read(file)
        val hooks = (root["hooks"] as? JsonObject) ?: JsonObject(emptyMap())
        val merged = buildJsonObject {
            hooks.forEach { (event, groups) -> if (event !in EVENTS) put(event, groups) }
            EVENTS.forEach { event ->
                val kept = (hooks[event] as? JsonArray)?.filterNot { it.isOurs() }.orEmpty()
                put(event, JsonArray(kept + ourGroup()))
            }
        }
        write(file, JsonObject(root + ("hooks" to merged)))
        ensureGitIgnored(projectDir)
    }

    fun uninstall(projectDir: Path) {
        val file = settingsFile(projectDir)
        if (!file.exists()) return
        val root = read(file)
        val hooks = root["hooks"] as? JsonObject ?: return
        val cleaned = buildMap<String, JsonElement> {
            hooks.forEach { (event, groups) ->
                if (groups !is JsonArray) {
                    put(event, groups)
                } else {
                    val kept = groups.filterNot { it.isOurs() }
                    if (kept.isNotEmpty()) put(event, JsonArray(kept))
                }
            }
        }
        write(file, JsonObject(if (cleaned.isEmpty()) root - "hooks" else root + ("hooks" to JsonObject(cleaned))))
    }

    private fun ourGroup() = buildJsonObject {
        put(
            "hooks",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "http")
                        put("url", hookUrl)
                        // Short: a hook must never hold Claude up; if the agent is not running, Claude just continues.
                        put("timeout", TIMEOUT_S)
                        put("headers", buildJsonObject { put(SECRET_HEADER, secret) })
                    },
                )
            },
        )
    }

    /** A matcher group is ours when one of its hooks posts to our URL. */
    private fun JsonElement.isOurs(): Boolean =
        (this as? JsonObject)?.get("hooks").let { it as? JsonArray }
            ?.any { ((it as? JsonObject)?.get("url") as? JsonPrimitive)?.content == hookUrl } == true

    private fun read(file: Path): JsonObject {
        if (!file.exists()) return JsonObject(emptyMap())
        val text = file.readText()
        if (text.isBlank()) return JsonObject(emptyMap())
        return runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse {
            // Never overwrite a file we cannot understand; the user may be mid-edit.
            throw HookInstallException("${file.fileName} is not valid JSON. Fix it and try again.")
        }
    }

    private fun write(file: Path, content: JsonObject) {
        file.parent.createDirectories()
        file.writeText(json.encodeToString(JsonObject.serializer(), content) + "\n")
        AppStorage.restrictToOwner(file, directory = false)
    }

    private fun ensureGitIgnored(projectDir: Path) {
        if (!projectDir.resolve(".git").exists()) return
        val gitignore = projectDir.resolve(".gitignore")
        val lines = if (gitignore.exists()) gitignore.readText().lines().map { it.trim() } else emptyList()
        val covered = lines.any { it in GITIGNORE_PATTERNS }
        if (!covered) {
            val prefix = if (gitignore.exists() && !gitignore.readText().endsWith("\n")) "\n" else ""
            gitignore.toFile().appendText("$prefix# AgentMon hook secret (Claude Code local settings)\n.claude/settings.local.json\n")
        }
    }

    companion object {
        const val SECRET_HEADER = "X-Agentmon-Secret"
        private const val TIMEOUT_S = 5

        /**
         * Events posted to the agent. SessionStart is not included: it only supports command hooks, and
         * a session appears on its first prompt or tool call anyway.
         */
        val EVENTS = listOf(
            "UserPromptSubmit",
            "PreToolUse",
            "PostToolUse",
            "PostToolUseFailure",
            "PermissionRequest",
            "Notification",
            "Stop",
            "StopFailure",
            "SessionEnd",
        )

        private val GITIGNORE_PATTERNS = setOf(
            ".claude/settings.local.json", "/.claude/settings.local.json", "settings.local.json",
            "**/settings.local.json", ".claude/", ".claude", "/.claude/", "/.claude",
        )

        /** Loads the hook secret, creating a random 256-bit one on first use. */
        fun loadOrCreateSecret(file: Path): String {
            if (file.exists()) file.readText().trim().takeIf { it.length >= MIN_SECRET }?.let { return it }
            val secret = AgentCrypto.randomToken(SECRET_BYTES)
            file.writeText(secret)
            AppStorage.restrictToOwner(file, directory = false)
            return secret
        }

        private const val SECRET_BYTES = 32
        private const val MIN_SECRET = 32
    }
}
