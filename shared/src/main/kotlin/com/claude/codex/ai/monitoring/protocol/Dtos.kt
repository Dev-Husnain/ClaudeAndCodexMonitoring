package com.claude.codex.ai.monitoring.protocol

import kotlinx.serialization.Serializable

@Serializable
data class ComputerDto(
    val computerId: String,
    val name: String,
)

@Serializable
data class ProjectDto(
    val projectId: String,
    val name: String,
)

@Serializable
data class SessionDto(
    val sessionId: String,
    val projectId: String,
    val state: SessionState,
    val controlMode: ControlMode,
    val startedAt: Long,
    val lastEventAt: Long,
    val lastMessageSnippet: String? = null,
    val lastTool: String? = null,
    val errorInfo: String? = null,
    /** Set while Away mode holds Claude for the owner's answer. */
    val awaiting: AwaitingDto? = null,
)

@Serializable
data class AwaitingDto(
    val kind: AwaitingKind,
    /** For PERMISSION: what Claude wants to do, e.g. "Bash: ./gradlew test". For REPLY: Claude's last message. */
    val detail: String? = null,
    val sinceMs: Long,
)

@Serializable
data class TimelineEventDto(
    val eventId: String,
    val sessionId: String,
    val ts: Long,
    val kind: EventKind,
    val title: String,
    val detail: String? = null,
)

/** One terminal line as runs of equally styled text. */
@Serializable
data class TerminalLineDto(val spans: List<TerminalSpanDto>)

/**
 * A run of text in one style. Colours are `null` for the terminal default, `0..255` for the xterm
 * palette (0..15 are the theme's ANSI colours), or [RGB_FLAG] or'ed with `0xRRGGBB`.
 */
@Serializable
data class TerminalSpanDto(
    val text: String,
    val fg: Int? = null,
    val bg: Int? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val dim: Boolean = false,
    /** Swap foreground and background (Claude Code draws its input cursor this way). */
    val inverse: Boolean = false,
) {
    companion object {
        const val RGB_FLAG = 1 shl 24
    }
}
