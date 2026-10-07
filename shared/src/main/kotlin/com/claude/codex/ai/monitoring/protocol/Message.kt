package com.claude.codex.ai.monitoring.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Every frame on the WebSocket. The [SerialName] of each subtype is the envelope `type`
 * (see [Envelope] and [ProtocolCodec]).
 */
@Serializable
sealed interface Message {

    // ---- Client -> Desktop ----

    @Serializable
    @SerialName("hello")
    data class Hello(val deviceId: String, val appVersion: String) : Message

    @Serializable
    @SerialName("auth")
    data class Auth(val signature: String) : Message

    @Serializable
    @SerialName("subscribe")
    data class Subscribe(val projectIds: List<String>) : Message

    @Serializable
    @SerialName("session.list")
    data object SessionList : Message

    @Serializable
    @SerialName("session.history")
    data class SessionHistory(val sessionId: String, val beforeTs: Long? = null) : Message

    @Serializable
    @SerialName("send_input")
    data class SendInput(val sessionId: String, val text: String) : Message

    @Serializable
    @SerialName("quick_action")
    data class QuickActionRequest(val sessionId: String, val action: QuickAction) : Message

    /** Turn Away mode on or off. Needs input permission; answered with `ack`. */
    @Serializable
    @SerialName("away.set")
    data class SetAwayMode(val enabled: Boolean) : Message

    /**
     * The projects Claude Code worked in on this computer. Only for phones with access to all projects; answered
     * with [AvailableProjectsResult].
     */
    @Serializable
    @SerialName("projects.available")
    data object AvailableProjects : Message

    /** Start watching a project from [AvailableProjectsResult]. Needs input permission; `ack.detail` = its id. */
    @Serializable
    @SerialName("project.add")
    data class AddProject(val projectId: String) : Message

    /** The saved conversations of a project, newest first. Answered with [PastSessionsResult]. */
    @Serializable
    @SerialName("sessions.past")
    data class PastSessions(val projectId: String) : Message

    /**
     * Continue a saved conversation with [text] as the next prompt (`claude -p --resume`). Needs input
     * permission; answered with `ack`, whose `detail` is the session id to open on success.
     */
    @Serializable
    @SerialName("session.resume")
    data class ResumeSession(val projectId: String, val claudeSessionId: String, val text: String) : Message

    /**
     * Open a terminal on the computer running Claude through `agentmon claude`: continuing [claudeSessionId], or a
     * new conversation when it is null. Needs input permission; `ack.detail` is the session id to open.
     */
    @Serializable
    @SerialName("session.start")
    data class StartTerminal(val projectId: String, val claudeSessionId: String? = null) : Message

    /** Start receiving [TerminalScreen] for a wrapper session (one terminal per connection). */
    @Serializable
    @SerialName("terminal.attach")
    data class TerminalAttach(val sessionId: String) : Message

    @Serializable
    @SerialName("terminal.detach")
    data object TerminalDetach : Message

    /** A key pressed on the phone's terminal keys row. Needs input permission; answered with `ack`. */
    @Serializable
    @SerialName("terminal.key")
    data class TerminalKeyRequest(val sessionId: String, val key: TerminalKey) : Message

    @Serializable
    @SerialName("ping")
    data object Ping : Message

    // ---- Desktop -> Client ----

    @Serializable
    @SerialName("challenge")
    data class Challenge(val nonce: String, val desktopSignature: String) : Message

    @Serializable
    @SerialName("ready")
    data class Ready(
        val computer: ComputerDto,
        val projects: List<ProjectDto>,
        val sessions: List<SessionDto>,
        /** False for read-only devices (spec 6.2 grant); the phone hides input controls. */
        val canSendInput: Boolean = false,
        val awayMode: Boolean = false,
    ) : Message

    /** Away mode changed (from the desktop or any phone). */
    @Serializable
    @SerialName("away.update")
    data class AwayModeUpdate(val enabled: Boolean) : Message

    @Serializable
    @SerialName("session.update")
    data class SessionUpdate(val session: SessionDto) : Message

    /** The desktop forgot a session (ended long ago, or its project was removed). Not in the original spec. */
    @Serializable
    @SerialName("session.removed")
    data class SessionRemoved(val sessionId: String, val projectId: String) : Message

    @Serializable
    @SerialName("session.event")
    data class SessionEvent(val sessionId: String, val event: TimelineEventDto) : Message

    /** Reply to [SessionHistory]; events are oldest first. Not in the original spec, see docs/protocol.md. */
    @Serializable
    @SerialName("session.history.result")
    data class SessionHistoryResult(
        val sessionId: String,
        val events: List<TimelineEventDto>,
        val hasMore: Boolean,
    ) : Message

    /**
     * What the wrapper's terminal shows: the newest lines (scrollback + screen), already interpreted by
     * the desktop's terminal emulator. Sent only to phones attached to that session, when it changes.
     * Replaces the spec's raw `terminal.chunk`: Claude Code redraws with cursor movement, so raw output
     * cannot be shown line by line.
     */
    @Serializable
    @SerialName("terminal.screen")
    data class TerminalScreen(val sessionId: String, val columns: Int, val lines: List<TerminalLineDto>) : Message

    /** Reply to [PastSessions]. */
    @Serializable
    @SerialName("sessions.past.result")
    data class PastSessionsResult(val projectId: String, val sessions: List<PastSessionDto>) : Message

    /** Reply to [AvailableProjects]; [allowed] is false for phones limited to chosen projects (the list is then empty). */
    @Serializable
    @SerialName("projects.available.result")
    data class AvailableProjectsResult(val allowed: Boolean, val projects: List<AvailableProjectDto>) : Message

    @Serializable
    @SerialName("ack")
    data class Ack(val ackId: String, val result: DeliveryResult, val detail: String? = null) : Message

    @Serializable
    @SerialName("error")
    data class Error(val code: ErrorCode, val message: String, val ackId: String? = null) : Message

    @Serializable
    @SerialName("pong")
    data object Pong : Message

    @Serializable
    @SerialName("revoked")
    data object Revoked : Message
}
