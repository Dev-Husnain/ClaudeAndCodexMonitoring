package com.claude.codex.ai.monitoring.data.repo

import com.claude.codex.ai.monitoring.domain.models.AgentSnapshotModel
import com.claude.codex.ai.monitoring.domain.models.ConnectionStatus
import com.claude.codex.ai.monitoring.domain.models.SessionStatus
import com.claude.codex.ai.monitoring.protocol.ComputerDto
import com.claude.codex.ai.monitoring.protocol.ControlMode
import com.claude.codex.ai.monitoring.protocol.ErrorCode
import com.claude.codex.ai.monitoring.domain.models.AuthProblem
import com.claude.codex.ai.monitoring.protocol.EventKind
import com.claude.codex.ai.monitoring.protocol.Message
import com.claude.codex.ai.monitoring.protocol.ProjectDto
import com.claude.codex.ai.monitoring.protocol.ProtocolConstants
import com.claude.codex.ai.monitoring.protocol.SessionDto
import com.claude.codex.ai.monitoring.protocol.SessionState
import com.claude.codex.ai.monitoring.protocol.TimelineEventDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentStateReducerTest {

    private fun session(id: String, state: SessionState = SessionState.RUNNING) =
        SessionDto(id, "p1", state, ControlMode.MONITOR_ONLY, startedAt = 1, lastEventAt = 2)

    private fun event(id: String, ts: Long, sessionId: String = "s1") =
        TimelineEventDto(id, sessionId, ts, EventKind.TOOL_USE, "Edit")

    private val ready = Message.Ready(ComputerDto("c1", "Laptop"), listOf(ProjectDto("p1", "App")), listOf(session("s1")))

    @Test
    fun `ready marks the connection connected and replaces sessions`() {
        val state = AgentStateReducer.onMessage(AgentSnapshotModel(), ready, nowMs = 100)
        assertEquals(ConnectionStatus.Connected(100), state.connection)
        assertTrue(state.hasSnapshot)
        assertEquals("Laptop", state.computer?.name)
        assertEquals(listOf("s1"), state.sessions.map { it.sessionId })
        assertEquals(100L, state.lastConnectedAtMs)
    }

    @Test
    fun `ready drops timelines of sessions the computer no longer knows`() {
        val withHistory = AgentStateReducer.onMessage(
            AgentStateReducer.onMessage(AgentSnapshotModel(), ready, 1),
            Message.SessionHistoryResult("gone", listOf(event("e1", 1, "gone")), hasMore = false),
            2,
        )
        val state = AgentStateReducer.onMessage(withHistory, ready, 3)
        assertNull(state.timelines["gone"])
    }

    @Test
    fun `session update replaces an existing session and appends a new one`() {
        val base = AgentStateReducer.onMessage(AgentSnapshotModel(), ready, 1)
        val updated = AgentStateReducer.onMessage(base, Message.SessionUpdate(session("s1", SessionState.WAITING_INPUT)), 2)
        assertEquals(SessionStatus.WAITING_INPUT, updated.sessions.single().status)
        val appended = AgentStateReducer.onMessage(updated, Message.SessionUpdate(session("s2")), 3)
        assertEquals(listOf("s1", "s2"), appended.sessions.map { it.sessionId })
    }

    @Test
    fun `live events are ignored until history is loaded`() {
        val base = AgentStateReducer.onMessage(AgentSnapshotModel(), ready, 1)
        val state = AgentStateReducer.onMessage(base, Message.SessionEvent("s1", event("e1", 5)), 2)
        assertNull(state.timelines["s1"])
    }

    @Test
    fun `history and live events merge without duplicates in time order`() {
        val base = AgentStateReducer.onMessage(AgentSnapshotModel(), ready, 1)
        val withHistory = AgentStateReducer.onMessage(
            base,
            Message.SessionHistoryResult("s1", listOf(event("e2", 20), event("e1", 10)), hasMore = false),
            2,
        )
        val withLive = AgentStateReducer.onMessage(withHistory, Message.SessionEvent("s1", event("e2", 20)), 3)
        val final = AgentStateReducer.onMessage(withLive, Message.SessionEvent("s1", event("e3", 30)), 4)
        assertEquals(listOf("e1", "e2", "e3"), final.timelines.getValue("s1").map { it.eventId })
    }

    @Test
    fun `timeline is capped to the newest events`() {
        val base = AgentStateReducer.onMessage(AgentSnapshotModel(), ready, 1)
        val many = (1..ProtocolConstants.TIMELINE_CAPACITY + 50).map { event("e$it", it.toLong()) }
        val state = AgentStateReducer.onMessage(base, Message.SessionHistoryResult("s1", many, hasMore = true), 2)
        val timeline = state.timelines.getValue("s1")
        assertEquals(ProtocolConstants.TIMELINE_CAPACITY, timeline.size)
        assertEquals("e${ProtocolConstants.TIMELINE_CAPACITY + 50}", timeline.last().eventId)
    }

    @Test
    fun `disconnects become reconnecting and then offline keeping last seen`() {
        val connected = AgentStateReducer.onMessage(AgentSnapshotModel(), ready, 42)
        assertEquals(ConnectionStatus.Reconnecting(1), AgentStateReducer.onDisconnected(connected, 1).connection)
        val offline = AgentStateReducer.onDisconnected(connected, AgentStateReducer.OFFLINE_AFTER_FAILURES)
        assertIs<ConnectionStatus.Offline>(offline.connection)
        assertEquals(42L, (offline.connection as ConnectionStatus.Offline).lastConnectedAtMs)
        assertEquals(1, offline.sessions.size)
    }

    @Test
    fun `revoked and auth refusals are recognised, other errors are not`() {
        assertEquals(AuthProblem.REVOKED, AgentStateReducer.refusalOf(Message.Revoked))
        assertEquals(AuthProblem.NOT_PAIRED, AgentStateReducer.refusalOf(Message.Error(ErrorCode.NOT_PAIRED, "")))
        assertEquals(AuthProblem.AUTH_FAILED, AgentStateReducer.refusalOf(Message.Error(ErrorCode.AUTH_FAILED, "")))
        assertNull(AgentStateReducer.refusalOf(Message.Error(ErrorCode.RATE_LIMITED, "")))
        assertNull(AgentStateReducer.refusalOf(Message.Pong))
    }

    @Test
    fun `unauthorized clears everything learned from the computer`() {
        val connected = AgentStateReducer.onMessage(AgentSnapshotModel(), ready.copy(canSendInput = true), 1)
        assertTrue(connected.canSendInput)
        val refused = AgentStateReducer.onUnauthorized(connected, AuthProblem.REVOKED)
        assertEquals(ConnectionStatus.Unauthorized(AuthProblem.REVOKED), refused.connection)
        assertTrue(refused.sessions.isEmpty())
        assertEquals(false, refused.canSendInput)
    }

    @Test
    fun `removed sessions disappear with their timeline`() {
        val base = AgentStateReducer.onMessage(AgentSnapshotModel(), ready, 1)
        val withHistory = AgentStateReducer.onMessage(base, Message.SessionHistoryResult("s1", listOf(event("e1", 1)), false), 2)
        val removed = AgentStateReducer.onMessage(withHistory, Message.SessionRemoved("s1", "p1"), 3)
        assertTrue(removed.sessions.isEmpty())
        assertNull(removed.timelines["s1"])
    }
}
