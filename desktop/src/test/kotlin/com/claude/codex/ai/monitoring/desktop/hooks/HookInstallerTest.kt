package com.claude.codex.ai.monitoring.desktop.hooks

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HookInstallerTest {

    private val project: Path = Files.createTempDirectory("agentmon-project")
    private val installer = HookInstaller(secret = "s".repeat(43), hookUrl = "http://127.0.0.1:8787/hook")
    private val settings = installer.settingsFile(project)

    private fun readSettings(): JsonObject = Json.parseToJsonElement(settings.readText()).jsonObject

    private fun existing(json: String) {
        settings.parent.createDirectories()
        settings.writeText(json)
    }

    @Test
    fun `install on an empty project adds every event with the secret header`() {
        installer.install(project)
        val hooks = readSettings().getValue("hooks").jsonObject
        HookInstaller.EVENTS.forEach { event ->
            val hook = hooks.getValue(event).jsonArray.single().jsonObject.getValue("hooks").jsonArray.single().jsonObject
            assertEquals("http", hook.getValue("type").jsonPrimitive.content)
            val headers = hook.getValue("headers").jsonObject
            assertEquals("s".repeat(43), headers.getValue(HookInstaller.SECRET_HEADER).jsonPrimitive.content)
            // The wrapper id comes from Claude's environment, and only that variable may be interpolated.
            assertEquals("\$AGENTMON_WRAPPER_ID", headers.getValue("X-Agentmon-Wrapper").jsonPrimitive.content)
            assertEquals(listOf("AGENTMON_WRAPPER_ID"), hook.getValue("allowedEnvVars").jsonArray.map { it.jsonPrimitive.content })
        }
        assertTrue(installer.isInstalled(project))
    }

    @Test
    fun `user hooks and other settings are kept and installing twice adds nothing`() {
        existing(
            """
            {"permissions":{"allow":["Bash(npm test)"]},
             "hooks":{"PreToolUse":[{"matcher":"Bash","hooks":[{"type":"command","command":"./my-guard.sh"}]}],
                      "PreCompact":[{"hooks":[{"type":"command","command":"./save.sh"}]}]}}
            """.trimIndent(),
        )
        installer.install(project)
        installer.install(project)
        val root = readSettings()
        assertEquals("Bash(npm test)", root.getValue("permissions").jsonObject.getValue("allow").jsonArray.single().jsonPrimitive.content)
        val pre = root.getValue("hooks").jsonObject.getValue("PreToolUse").jsonArray
        assertEquals(2, pre.size, "user guard + ours, no duplicate")
        assertTrue(root.getValue("hooks").jsonObject.containsKey("PreCompact"))
    }

    @Test
    fun `uninstall removes only our hooks`() {
        existing("""{"hooks":{"PreToolUse":[{"matcher":"Bash","hooks":[{"type":"command","command":"./my-guard.sh"}]}]}}""")
        installer.install(project)
        installer.uninstall(project)
        val hooks = readSettings().getValue("hooks").jsonObject
        assertEquals(setOf("PreToolUse"), hooks.keys)
        assertEquals("./my-guard.sh", (hooks.getValue("PreToolUse") as JsonArray).single().jsonObject.getValue("hooks").jsonArray.single().jsonObject.getValue("command").jsonPrimitive.content)
        assertFalse(installer.isInstalled(project))
    }

    @Test
    fun `uninstall of a file with only our hooks leaves no hooks key`() {
        installer.install(project)
        installer.uninstall(project)
        assertFalse(readSettings().containsKey("hooks"))
    }

    @Test
    fun `broken json is never overwritten`() {
        existing("{ this is not json")
        assertFailsWith<HookInstallException> { installer.install(project) }
        assertEquals("{ this is not json", settings.readText())
    }

    @Test
    fun `the settings file is added to gitignore in git projects`() {
        project.resolve(".git").createDirectories()
        project.resolve(".gitignore").writeText("/build")
        installer.install(project)
        installer.install(project)
        val ignore = project.resolve(".gitignore").readText()
        assertTrue(ignore.contains(".claude/settings.local.json"))
        assertEquals(1, Regex("settings.local.json").findAll(ignore).count())
    }
}
