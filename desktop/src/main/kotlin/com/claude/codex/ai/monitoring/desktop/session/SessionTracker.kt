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
    /** The conversation title in a transcript Claude Code reported, or null. */
    private val transcriptTitle: (transcriptPath: String) -> String? = { null },
) {
    /** Last hook received per project id, shown on the Projects screen as a health check. */
    private val _lastHookAt = MutableStateFlow<Map<String, Long>>(emptyMap())
    val lastHookAt: StateFlow<Map<String, Long>> = _lastHookAt.asStateFlow()

    /** Returns false when the event belongs to no monitored project (ignored). */
    @Synchronized
    fun onHook(hook: HookEventDto): Boolean {
        val project = projects.projectFor(hook.cwd) ?: return false
        val now = clock()
        // File reading stays outside the registry lock.
        val title = titleFrom(hook)
        _lastHookAt.update { it + (project.projectId to now) }
        // Read and write in one step: a concurrent Away-mode hold must not be overwritten by a stale copy.
        var event: TimelineEventDto? = null
        registry.mutateSession(hook.sessionId) { current ->
            val applied = SessionStateMachine.apply(current, hook, project.projectId, now)?.also { event = it.event }?.session
            val base = applied ?: current ?: return@mutateSession null
            val titled = when {
                title is TitleUpdate.Set -> base.copy(title = title.title)
                title is TitleUpdate.Clear -> base.copy(title = null)
                title is TitleUpdate.IfMissing && base.title == null -> base.copy(title = title.title)
                else -> base
            }
            titled.takeIf { applied != null || it != current }
        }
        event?.let(registry::addEvent)
        return true
    }

    private sealed interface TitleUpdate {
        data class Set(val title: String) : TitleUpdate
        data class IfMissing(val title: String) : TitleUpdate
        data object Clear : TitleUpdate
        data object Keep : TitleUpdate
    }

    /**
     * After each answer (`Stop`) the transcript has the best title (Claude names conversations as they go).
     * A new conversation (`SessionStart`, also after `/clear` in the same terminal) starts from its transcript,
     * which is empty for a fresh one. Until then the first typed prompt stands in.
     */
    private fun titleFrom(hook: HookEventDto): TitleUpdate {
        val fromTranscript = { hook.transcriptPath?.let(transcriptTitle)?.let(::shorten) }
        return when (hook.eventName) {
            "Stop" -> fromTranscript()?.let { TitleUpdate.Set(it) } ?: TitleUpdate.Keep
            "SessionStart" -> fromTranscript()?.let { TitleUpdate.Set(it) } ?: TitleUpdate.Clear
            "UserPromptSubmit" -> hook.prompt?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("/") && !it.startsWith("<") }
                ?.let(::shorten)?.let { TitleUpdate.IfMissing(it) } ?: TitleUpdate.Keep
            else -> TitleUpdate.Keep
        }
    }

    private fun shorten(text: String): String? =
        text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.let { if (it.length > TITLE_CHARS) it.take(TITLE_CHARS - 1).trimEnd() + "…" else it }

    /** Remembers Claude's own id for a session shown under another id, so it can be resumed later. */
    fun recordClaudeSessionId(sessionId: String, claudeSessionId: String) {
        registry.mutateSession(sessionId) { current ->
            current?.takeIf { it.claudeSessionId != claudeSessionId }?.copy(claudeSessionId = claudeSessionId)
        }
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

    private companion object {
        const val TITLE_CHARS = 80
    }
}
