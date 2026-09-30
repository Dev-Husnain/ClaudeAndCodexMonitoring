package com.claude.codex.ai.monitoring.desktop.session

import com.claude.codex.ai.monitoring.desktop.hooks.HookEventDto
import com.claude.codex.ai.monitoring.desktop.projects.ProjectStore
import com.claude.codex.ai.monitoring.protocol.SessionState
import com.claude.codex.ai.monitoring.protocol.TimelineEventDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Turns Claude Code hook events into session updates in the [SessionRegistry], marks silent
 * sessions as possibly stuck, and forgets sessions that ended long ago.
 */
class SessionTracker(
    private val registry: SessionRegistry,
    private val projects: ProjectStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val staleAfterMs: Long = 15 * 60_000L,
    private val forgetEndedAfterMs: Long = 60 * 60_000L,
    private val forgetIdleAfterMs: Long = 24 * 60 * 60_000L,
) {
    /** Last hook received per project id, shown on the Projects screen as a health check. */
    private val _lastHookAt = MutableStateFlow<Map<String, Long>>(emptyMap())
    val lastHookAt: StateFlow<Map<String, Long>> = _lastHookAt.asStateFlow()

    /** Returns false when the event belongs to no monitored project (ignored). */
    @Synchronized
    fun onHook(hook: HookEventDto): Boolean {
        val project = projects.projectFor(hook.cwd) ?: return false
        val now = clock()
        _lastHookAt.update { it + (project.projectId to now) }
        // Read and write in one step: a concurrent Away-mode hold must not be overwritten by a stale copy.
        var event: TimelineEventDto? = null
        registry.mutateSession(hook.sessionId) { current ->
            SessionStateMachine.apply(current, hook, project.projectId, now)?.also { event = it.event }?.session
        }
        event?.let(registry::addEvent)
        return true
    }

    /** Runs until cancelled: stale detection and cleanup every [periodMs]. */
    suspend fun runMaintenance(periodMs: Long = 30_000L) {
        while (coroutineContext.isActive) {
            maintain()
            delay(periodMs)
        }
    }

    @Synchronized
    fun maintain() {
        val now = clock()
        registry.state.value.sessions.forEach { session ->
            registry.mutateSession(session.sessionId) { current -> current?.let { SessionStateMachine.markStale(it, now, staleAfterMs) } }
            val age = now - session.lastEventAt
            val forget = when (session.state) {
                SessionState.ENDED -> age >= forgetEndedAfterMs
                SessionState.IDLE, SessionState.ERROR, SessionState.STALE -> age >= forgetIdleAfterMs
                else -> false
            }
            if (forget) registry.removeSession(session.sessionId)
        }
    }
}
