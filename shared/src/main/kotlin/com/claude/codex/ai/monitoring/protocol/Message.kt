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

    @Serializable
    @SerialName("terminal.attach")
    data class TerminalAttach(val sessionId: String) : Message

    @Serializable
    @SerialName("terminal.detach")
    data object TerminalDetach : Message

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
    ) : Message

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

    @Serializable
    @SerialName("terminal.chunk")
    data class TerminalChunk(val sessionId: String, val data: String) : Message

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
