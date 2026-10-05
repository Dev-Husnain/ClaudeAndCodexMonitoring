package com.claude.codex.ai.monitoring.desktop.projects

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * The name a person knows a project by, for the phone: the name the IDE or build file gives it, else the
 * folder name. Slugs such as `my-shop_app` are shown as "My Shop App"; names that already have their own
 * capitals (`ClaudeMonitoring`, `iOSApp`) are kept as written.
 */
object ProjectNamer {

    fun nameFor(dir: Path, fallback: String = dir.name): String {
        val declared = runCatching { declaredName(dir) }.getOrNull()
        return humanize(declared ?: fallback).ifBlank { humanize(fallback) }.ifBlank { DEFAULT_NAME }
    }

    /** The first name found in the IDE's settings or a build file, in that order. */
    private fun declaredName(dir: Path): String? =
        read(dir.resolve(".idea").resolve(".name"))?.lineSequence()?.firstOrNull()
            ?: gradleName(dir)
            ?: read(dir.resolve("package.json"))?.let { PACKAGE_JSON_NAME.find(it)?.groupValues?.get(1) }?.substringAfterLast('/')
            ?: read(dir.resolve("Cargo.toml"))?.let { tomlName(it, "package") }
            ?: read(dir.resolve("pyproject.toml"))?.let { tomlName(it, "project") ?: tomlName(it, "tool.poetry") }

    private fun gradleName(dir: Path): String? =
        listOf("settings.gradle.kts", "settings.gradle").firstNotNullOfOrNull { file ->
            read(dir.resolve(file))?.let { GRADLE_NAME.find(it)?.groupValues?.get(1) }
        }

    /** `name = "x"` inside `[section]` of a TOML file. */
    private fun tomlName(text: String, section: String): String? {
        var inSection = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("[")) {
                inSection = line == "[$section]"
                continue
            }
            if (inSection) TOML_NAME.matchEntire(line)?.let { return it.groupValues[1] }
        }
        return null
    }

    private fun read(file: Path): String? =
        file.takeIf { it.isRegularFile() && Files.size(it) <= MAX_FILE_BYTES }?.let { Files.readString(it) }?.trim()?.ifBlank { null }

    /** "agentmon-hooktest" -> "Agentmon Hooktest"; "ClaudeMonitoring" stays. */
    fun humanize(raw: String): String {
        val name = raw.trim().take(MAX_NAME_CHARS)
        val words = name.split(SEPARATORS).filter { it.isNotEmpty() }
        val isSlug = words.size > 1 || name.none { it.isUpperCase() }
        if (!isSlug) return name
        return words.joinToString(" ") { word ->
            if (word.any { it.isUpperCase() }) word else word.replaceFirstChar { it.titlecase() }
        }
    }

    const val DEFAULT_NAME = "Claude Code"
    private const val MAX_FILE_BYTES = 256 * 1024L
    private const val MAX_NAME_CHARS = 60
    private val SEPARATORS = Regex("[-_.\\s]+")
    private val GRADLE_NAME = Regex("""rootProject\.name\s*=\s*["']([^"']+)["']""")
    private val PACKAGE_JSON_NAME = Regex(""""name"\s*:\s*"([^"]+)"""")
    private val TOML_NAME = Regex("""name\s*=\s*["']([^"']+)["'].*""")
}
