package com.claude.codex.ai.monitoring.desktop.session

import com.claude.codex.ai.monitoring.desktop.hooks.HookEventDto
import com.claude.codex.ai.monitoring.desktop.projects.ProjectStore
import com.claude.codex.ai.monitoring.desktop.storage.AppStorage
import com.claude.codex.ai.monitoring.protocol.ComputerDto
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionTitleTest {

    private val projectDir = Files.createTempDirectory("agentmon-titles")
    private val projects = ProjectStore(AppStorage.openDatabase(null)).apply { add(projectDir) }
    private val registry = SessionRegistry(ComputerDto("c1", "PC"))
    private val transcriptTitles = mutableMapOf<String, String>()
    private val tracker = SessionTracker(registry, projects, transcriptTitle = { transcriptTitles[it] })

    private fun hook(event: String, prompt: String? = null) =
        HookEventDto(sessionId = "s1", eventName = event, cwd = projectDir.toString(), transcriptPath = "t1", prompt = prompt)

    private fun title() = registry.session("s1")?.title

    @Test
    fun `the first prompt stands in until Claude's own title is known, and a new conversation starts over`() {
        tracker.onHook(hook("SessionStart"))
        assertNull(title())

        tracker.onHook(hook("UserPromptSubmit", prompt = "/model sonnet"))
        assertNull(title(), "commands are not topics")
        tracker.onHook(hook("UserPromptSubmit", prompt = "\n  Fix the login crash on Android 14\nIt happens after the splash"))
        assertEquals("Fix the login crash on Android 14", title())
        tracker.onHook(hook("UserPromptSubmit", prompt = "also add a test"))
        assertEquals("Fix the login crash on Android 14", title(), "later prompts do not rename it")

        transcriptTitles["t1"] = "Login crash fix"
        tracker.onHook(hook("Stop"))
        assertEquals("Login crash fix", title())

        // `/clear` in the same terminal: a fresh conversation with nothing in its transcript yet.
        transcriptTitles.clear()
        tracker.onHook(hook("SessionStart"))
        assertNull(title())
    }

    @Test
    fun `long topics are shortened`() {
        tracker.onHook(hook("UserPromptSubmit", prompt = "x".repeat(300)))
        assertEquals(80, title()!!.length)
        assertEquals('…', title()!!.last())
    }
}
