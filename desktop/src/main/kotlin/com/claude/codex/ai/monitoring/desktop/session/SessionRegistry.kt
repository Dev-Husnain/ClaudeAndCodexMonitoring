package com.claude.codex.ai.monitoring.desktop.session

import com.claude.codex.ai.monitoring.protocol.ComputerDto
import com.claude.codex.ai.monitoring.protocol.Message
import com.claude.codex.ai.monitoring.protocol.ProjectDto
import com.claude.codex.ai.monitoring.protocol.ProtocolConstants
import com.claude.codex.ai.monitoring.protocol.SessionDto
import com.claude.codex.ai.monitoring.protocol.TimelineEventDto
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Immutable view of everything the agent knows, for the desktop UI. */
data class RegistryState(
    val projects: List<ProjectDto> = emptyList(),
    val sessions: List<SessionDto> = emptyList(),
)

/**
 * Single source of truth for projects, sessions and per-session timelines on the desktop.
 * Every change is published on [updates] so connected phones receive it live.
 */
class SessionRegistry(
    val computer: ComputerDto,
    private val timelineCapacity: Int = ProtocolConstants.TIMELINE_CAPACITY,
) {
    private val _state = MutableStateFlow(RegistryState())
    val state: StateFlow<RegistryState> = _state.asStateFlow()

    private val timelines = HashMap<String, ArrayDeque<TimelineEventDto>>()
    private val lock = Any()

    private val _updates = MutableSharedFlow<Message>(
        extraBufferCapacity = UPDATE_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val updates: SharedFlow<Message> = _updates.asSharedFlow()

    fun snapshot(): Message.Ready = _state.value.let { Message.Ready(computer, it.projects, it.sessions) }

    fun projectOf(sessionId: String): String? = _state.value.sessions.firstOrNull { it.sessionId == sessionId }?.projectId

    fun upsertProject(project: ProjectDto) {
        _state.update { state ->
            if (state.projects.any { it.projectId == project.projectId }) {
                state.copy(projects = state.projects.map { if (it.projectId == project.projectId) project else it })
            } else {
                state.copy(projects = state.projects + project)
            }
        }
    }

    fun upsertSession(session: SessionDto) {
        val trimmed = session.copy(
            lastMessageSnippet = session.lastMessageSnippet?.take(ProtocolConstants.MAX_TEXT_CHARS),
            errorInfo = session.errorInfo?.take(ProtocolConstants.MAX_TEXT_CHARS),
        )
        _state.update { state ->
            if (state.sessions.any { it.sessionId == trimmed.sessionId }) {
                state.copy(sessions = state.sessions.map { if (it.sessionId == trimmed.sessionId) trimmed else it })
            } else {
                state.copy(sessions = state.sessions + trimmed)
            }
        }
        _updates.tryEmit(Message.SessionUpdate(trimmed))
    }

    fun addEvent(event: TimelineEventDto) {
        val trimmed = event.copy(detail = event.detail?.take(ProtocolConstants.MAX_TEXT_CHARS))
        synchronized(lock) {
            val timeline = timelines.getOrPut(trimmed.sessionId) { ArrayDeque() }
            timeline.addLast(trimmed)
            while (timeline.size > timelineCapacity) timeline.removeFirst()
        }
        _updates.tryEmit(Message.SessionEvent(trimmed.sessionId, trimmed))
    }

    /** Up to [limit] events older than [beforeTs] (or the newest ones), oldest first. */
    fun history(sessionId: String, beforeTs: Long? = null, limit: Int = timelineCapacity): Message.SessionHistoryResult {
        val all = synchronized(lock) { timelines[sessionId]?.toList().orEmpty() }
        val eligible = if (beforeTs == null) all else all.filter { it.ts < beforeTs }
        val page = eligible.takeLast(limit)
        return Message.SessionHistoryResult(sessionId, page, hasMore = eligible.size > page.size)
    }

    private companion object {
        const val UPDATE_BUFFER = 256
    }
}
