package com.claude.codex.ai.monitoring.data.repo

import com.claude.codex.ai.monitoring.data.mapper.toModel
import com.claude.codex.ai.monitoring.domain.models.AgentSnapshotModel
import com.claude.codex.ai.monitoring.domain.models.ConnectionStatus
import com.claude.codex.ai.monitoring.domain.models.TimelineEventModel
import com.claude.codex.ai.monitoring.protocol.Message
import com.claude.codex.ai.monitoring.protocol.ProtocolConstants

/** Pure state transitions for the agent snapshot, kept separate from I/O so they can be unit tested. */
object AgentStateReducer {

    /** After this many consecutive failed attempts the computer is shown as offline. */
    const val OFFLINE_AFTER_FAILURES = 3

    fun onMessage(state: AgentSnapshotModel, message: Message, nowMs: Long): AgentSnapshotModel = when (message) {
        is Message.Ready -> {
            val sessions = message.sessions.map { it.toModel() }
            val known = sessions.mapTo(HashSet()) { it.sessionId }
            state.copy(
                connection = ConnectionStatus.Connected(sinceMs = nowMs),
                computer = message.computer.toModel(),
                projects = message.projects.map { it.toModel() },
                sessions = sessions,
                timelines = state.timelines.filterKeys { it in known },
                hasSnapshot = true,
                lastConnectedAtMs = nowMs,
            )
        }

        is Message.SessionUpdate -> {
            val updated = message.session.toModel()
            val exists = state.sessions.any { it.sessionId == updated.sessionId }
            state.copy(
                sessions = if (exists) {
                    state.sessions.map { if (it.sessionId == updated.sessionId) updated else it }
                } else {
                    state.sessions + updated
                },
                lastConnectedAtMs = nowMs,
            )
        }

        is Message.SessionEvent -> {
            // Only extend a timeline that was loaded; otherwise it would look complete when it is not.
            val existing = state.timelines[message.sessionId]
            if (existing == null) {
                state
            } else {
                state.copy(
                    timelines = state.timelines + (message.sessionId to merge(existing, listOf(message.event.toModel()))),
                    lastConnectedAtMs = nowMs,
                )
            }
        }

        is Message.SessionHistoryResult -> {
            val existing = state.timelines[message.sessionId].orEmpty()
            state.copy(
                timelines = state.timelines + (message.sessionId to merge(existing, message.events.map { it.toModel() })),
            )
        }

        Message.Pong -> state.copy(lastConnectedAtMs = nowMs)

        else -> state
    }

    fun onDisconnected(state: AgentSnapshotModel, consecutiveFailures: Int): AgentSnapshotModel = state.copy(
        connection = if (consecutiveFailures >= OFFLINE_AFTER_FAILURES) {
            ConnectionStatus.Offline(lastConnectedAtMs = state.lastConnectedAtMs)
        } else {
            ConnectionStatus.Reconnecting(attempt = consecutiveFailures)
        },
    )

    private fun merge(existing: List<TimelineEventModel>, incoming: List<TimelineEventModel>): List<TimelineEventModel> =
        (existing + incoming)
            .distinctBy { it.eventId }
            .sortedBy { it.timestampMs }
            .takeLast(ProtocolConstants.TIMELINE_CAPACITY)
}
