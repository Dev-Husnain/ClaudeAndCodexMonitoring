package com.claude.codex.ai.monitoring.desktop.simulator

import com.claude.codex.ai.monitoring.desktop.session.SessionRegistry
import com.claude.codex.ai.monitoring.protocol.ControlMode
import com.claude.codex.ai.monitoring.protocol.EventKind
import com.claude.codex.ai.monitoring.protocol.ProjectDto
import com.claude.codex.ai.monitoring.protocol.SessionDto
import com.claude.codex.ai.monitoring.protocol.SessionState
import com.claude.codex.ai.monitoring.protocol.TimelineEventDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/**
 * Phase 1 only: fake sessions that move through realistic states so the phone can be built and
 * tested before real Claude Code hooks exist (phase 4). Clearly labelled "Demo" in the UI.
 */
class DemoSessionSimulator(
    private val registry: SessionRegistry,
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Step(val state: SessionState, val kind: EventKind, val title: String, val detail: String?, val tool: String?)

    private val script = listOf(
        Step(SessionState.RUNNING, EventKind.PROMPT, "Prompt", "Add a reconnect back-off to the WebSocket client", null),
        Step(SessionState.RUNNING, EventKind.TOOL_USE, "Read", "app/src/main/java/.../AgentRepositoryImpl.kt", "Read"),
        Step(SessionState.RUNNING, EventKind.TOOL_USE, "Edit", "AgentRepositoryImpl.kt (+42 −7)", "Edit"),
        Step(SessionState.WAITING_INPUT, EventKind.NOTIFICATION, "Permission needed", "Claude needs your permission to use Bash: ./gradlew test", "Bash"),
        Step(SessionState.RUNNING, EventKind.TOOL_USE, "Bash", "./gradlew test", "Bash"),
        Step(SessionState.RUNNING, EventKind.TOOL_RESULT, "Tests passed", "23 tests, 0 failures", "Bash"),
        Step(SessionState.IDLE, EventKind.STOP, "Finished", "Back-off added and tests pass. Anything else?", null),
    )

    private val errorStep = Step(SessionState.ERROR, EventKind.ERROR, "API error", "Overloaded (529). The turn stopped.", null)

    suspend fun run() {
        val projects = listOf(ProjectDto("demo-monitor", "Demo · ClaudeMonitoring"), ProjectDto("demo-shop", "Demo · ShopKart"))
        projects.forEach(registry::upsertProject)
        val sessions = listOf(
            newSession("demo-monitor", ControlMode.WRAPPER),
            newSession("demo-monitor", ControlMode.MONITOR_ONLY),
            newSession("demo-shop", ControlMode.MONITOR_ONLY),
        )
        val positions = IntArray(sessions.size) { random.nextInt(script.size) }
        sessions.forEachIndexed { index, session -> apply(session.sessionId, session, script[positions[index]]) }

        val current = sessions.toMutableList()
        while (coroutineContext.isActive) {
            delay(random.nextLong(STEP_MIN_MS, STEP_MAX_MS))
            val index = random.nextInt(current.size)
            positions[index] = (positions[index] + 1) % script.size
            val step = if (random.nextInt(ERROR_ODDS) == 0) errorStep else script[positions[index]]
            current[index] = apply(current[index].sessionId, current[index], step)
        }
    }

    private fun newSession(projectId: String, controlMode: ControlMode): SessionDto {
        val now = clock()
        return SessionDto(
            sessionId = UUID.randomUUID().toString(),
            projectId = projectId,
            state = SessionState.RUNNING,
            controlMode = controlMode,
            startedAt = now - random.nextLong(60_000, 3_600_000),
            lastEventAt = now,
        )
    }

    private fun apply(sessionId: String, session: SessionDto, step: Step): SessionDto {
        val now = clock()
        val updated = session.copy(
            state = step.state,
            lastEventAt = now,
            lastMessageSnippet = step.detail,
            lastTool = step.tool ?: session.lastTool,
            errorInfo = if (step.state == SessionState.ERROR) step.detail else null,
        )
        registry.addEvent(TimelineEventDto(UUID.randomUUID().toString(), sessionId, now, step.kind, step.title, step.detail))
        registry.upsertSession(updated)
        return updated
    }

    private companion object {
        const val STEP_MIN_MS = 3_000L
        const val STEP_MAX_MS = 9_000L
        const val ERROR_ODDS = 25
    }
}
