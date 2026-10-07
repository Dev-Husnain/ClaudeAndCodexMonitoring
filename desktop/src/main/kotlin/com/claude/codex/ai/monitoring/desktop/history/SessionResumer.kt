package com.claude.codex.ai.monitoring.desktop.history

import com.claude.codex.ai.monitoring.desktop.control.Delivery
import com.claude.codex.ai.monitoring.desktop.session.SessionRegistry
import com.claude.codex.ai.monitoring.protocol.ControlMode
import com.claude.codex.ai.monitoring.protocol.DeliveryResult
import com.claude.codex.ai.monitoring.protocol.PastSessionDto
import com.claude.codex.ai.monitoring.protocol.SessionDto
import com.claude.codex.ai.monitoring.protocol.SessionState
import com.claude.codex.ai.monitoring.desktop.wrapper.TerminalLauncher
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Continues saved conversations from the phone. A conversation is resumed only while no Claude is
 * working in it: two processes on one conversation would interleave its transcript.
 */
class SessionResumer(
    private val registry: SessionRegistry,
    private val runner: HeadlessRunner,
    private val transcripts: TranscriptStore,
    /** The folder of a monitored project, or null. */
    private val projectPath: (projectId: String) -> Path?,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Opens terminals for "start Claude" from the phone; null where that is not available. */
    private val launcher: TerminalLauncher? = null,
    private val projectName: (projectId: String) -> String? = { null },
) {
    fun pastSessions(projectId: String): List<PastSessionDto>? = projectPath(projectId)?.let(transcripts::list)

    /**
     * Resumes [claudeSessionId] of [projectId] with [prompt]. On success the delivery's detail is the
     * AgentMon session id the phone should open.
     */
    fun resume(projectId: String, claudeSessionId: String, prompt: String): Delivery {
        if (!TranscriptStore.UUID.matches(claudeSessionId)) return Delivery(DeliveryResult.FAILED, "Unknown conversation")
        val project = projectPath(projectId) ?: return Delivery(DeliveryResult.FAILED, "This project is not monitored")
        val transcript = transcripts.find(project, claudeSessionId)
            ?: return Delivery(DeliveryResult.FAILED, "Claude has no saved conversation with this id in this project")
        val existing = registry.state.value.sessions.firstOrNull {
            it.sessionId == claudeSessionId || it.claudeSessionId == claudeSessionId
        }
        if (existing != null && isOpen(existing)) {
            return Delivery(DeliveryResult.FAILED, "Claude is still open in this conversation; send your message to it instead")
        }
        val sessionId = existing?.sessionId ?: claudeSessionId
        val now = clock()
        registry.mutateSession(sessionId) { current ->
            current?.copy(state = SessionState.RUNNING, controlMode = ControlMode.HEADLESS, awaiting = null, errorInfo = null, lastEventAt = now)
                ?: SessionDto(sessionId, projectId, SessionState.RUNNING, ControlMode.HEADLESS, now, now, lastMessageSnippet = prompt.lineSequence().first())
        }
        val workingDir = transcripts.workingDirectory(transcript)?.takeIf { Files.isDirectory(it) } ?: project
        val failure = runner.start(sessionId, claudeSessionId, workingDir, prompt)
        if (failure != null) {
            registry.mutateSession(sessionId) { it?.copy(state = SessionState.ENDED) }
            return Delivery(DeliveryResult.FAILED, failure)
        }
        return Delivery(DeliveryResult.DELIVERED, sessionId)
    }

    /**
     * Opens a terminal on this computer with `agentmon claude`, continuing [claudeSessionId] or starting a new
     * conversation. The session is created at once (with the id the wrapper will use), so the phone can open it
     * while the terminal starts. On success the delivery's detail is that session id.
     */
    fun startTerminal(projectId: String, claudeSessionId: String?): Delivery {
        val launch = launcher ?: return Delivery(DeliveryResult.FAILED, "Starting Claude from the phone is not available here")
        val project = projectPath(projectId) ?: return Delivery(DeliveryResult.FAILED, "This project is not monitored")
        var workingDir = project
        val args = mutableListOf<String>()
        if (claudeSessionId != null) {
            if (!TranscriptStore.UUID.matches(claudeSessionId)) return Delivery(DeliveryResult.FAILED, "Unknown conversation")
            val transcript = transcripts.find(project, claudeSessionId)
                ?: return Delivery(DeliveryResult.FAILED, "Claude has no saved conversation with this id in this project")
            val existing = registry.state.value.sessions.firstOrNull { it.sessionId == claudeSessionId || it.claudeSessionId == claudeSessionId }
            if (existing != null && isOpen(existing)) {
                return Delivery(DeliveryResult.FAILED, "Claude is still open in this conversation; send your message to it instead")
            }
            workingDir = transcripts.workingDirectory(transcript)?.takeIf { Files.isDirectory(it) } ?: project
            args += listOf("--resume", claudeSessionId)
        }
        val sessionId = UUID.randomUUID().toString()
        val now = clock()
        registry.mutateSession(sessionId) {
            SessionDto(
                sessionId, projectId, SessionState.IDLE, ControlMode.HOOKS, now, now,
                lastMessageSnippet = "Opening a terminal on your computer…",
                claudeSessionId = claudeSessionId,
            )
        }
        val failure = launch.launch(workingDir, sessionId, args, projectName(projectId) ?: workingDir.fileName?.toString().orEmpty())
        if (failure != null) {
            registry.removeSession(sessionId)
            return Delivery(DeliveryResult.FAILED, failure)
        }
        return Delivery(DeliveryResult.DELIVERED, sessionId)
    }

    /**
     * True while some Claude may still have this conversation open. An interactive session that hit an
     * API error waits at its prompt, so only an error of a resume run started here counts as closed.
     */
    fun isOpen(session: SessionDto): Boolean = when {
        runner.isRunning(session.sessionId) -> true
        session.state == SessionState.ENDED -> false
        session.state == SessionState.ERROR && session.controlMode == ControlMode.HEADLESS -> false
        else -> true
    }

    /** Resumes a session the agent knows that has ended (the phone typed into it). */
    fun resumeEnded(session: SessionDto, prompt: String): Delivery =
        resume(session.projectId, session.claudeSessionId ?: session.sessionId, prompt)
}
