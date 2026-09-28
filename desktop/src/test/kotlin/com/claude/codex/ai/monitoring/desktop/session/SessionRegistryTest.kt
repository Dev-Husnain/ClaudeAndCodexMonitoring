package com.claude.codex.ai.monitoring.desktop.session

import com.claude.codex.ai.monitoring.protocol.ComputerDto
import com.claude.codex.ai.monitoring.protocol.ControlMode
import com.claude.codex.ai.monitoring.protocol.EventKind
import com.claude.codex.ai.monitoring.protocol.ProjectDto
import com.claude.codex.ai.monitoring.protocol.ProtocolConstants
import com.claude.codex.ai.monitoring.protocol.SessionDto
import com.claude.codex.ai.monitoring.protocol.SessionState
import com.claude.codex.ai.monitoring.protocol.TimelineEventDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionRegistryTest {

    private val registry = SessionRegistry(ComputerDto("c1", "Laptop"), timelineCapacity = 5)

    private fun session(id: String, state: SessionState = SessionState.RUNNING, snippet: String? = null) =
        SessionDto(id, "p1", state, ControlMode.MONITOR_ONLY, 1, 2, lastMessageSnippet = snippet)

    private fun event(id: Int) = TimelineEventDto("e$id", "s1", id.toLong(), EventKind.TOOL_USE, "Edit")

    @Test
    fun `snapshot reflects upserted projects and sessions`() {
        registry.upsertProject(ProjectDto("p1", "App"))
        registry.upsertSession(session("s1"))
        registry.upsertSession(session("s1", SessionState.WAITING_INPUT))
        val ready = registry.snapshot()
        assertEquals("Laptop", ready.computer.name)
        assertEquals(1, ready.projects.size)
        assertEquals(SessionState.WAITING_INPUT, ready.sessions.single().state)
    }

    @Test
    fun `timeline keeps only the newest events up to capacity`() {
        (1..8).forEach { registry.addEvent(event(it)) }
        val history = registry.history("s1")
        assertEquals(listOf("e4", "e5", "e6", "e7", "e8"), history.events.map { it.eventId })
        assertFalse(history.hasMore)
    }

    @Test
    fun `history pages backwards from beforeTs`() {
        (1..5).forEach { registry.addEvent(event(it)) }
        val page = registry.history("s1", beforeTs = 4, limit = 2)
        assertEquals(listOf("e2", "e3"), page.events.map { it.eventId })
        assertTrue(page.hasMore)
    }

    @Test
    fun `free text sent to phones is truncated`() {
        registry.upsertSession(session("s1", snippet = "x".repeat(ProtocolConstants.MAX_TEXT_CHARS + 100)))
        assertEquals(ProtocolConstants.MAX_TEXT_CHARS, registry.snapshot().sessions.single().lastMessageSnippet?.length)
    }

    @Test
    fun `unknown session has empty history`() {
        assertTrue(registry.history("nope").events.isEmpty())
    }
}
