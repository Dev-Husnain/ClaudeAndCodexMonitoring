package com.claude.codex.ai.monitoring.protocol

import kotlinx.serialization.Serializable

@Serializable
enum class SessionState { RUNNING, WAITING_INPUT, IDLE, ERROR, ENDED, STALE }

/** How the desktop agent can deliver input into a session (spec 7.4). */
@Serializable
enum class ControlMode {
    /** Started without the wrapper: hooks give status, but input cannot be injected. */
    MONITOR_ONLY,

    /** Started via `agentmon claude`: input and quick actions go into the PTY. */
    WRAPPER,

    /** Idle session that can be continued with `claude -p --resume`. */
    HEADLESS,

    /**
     * Any session with AgentMon hooks: in Away mode the phone answers permission requests and
     * its messages reach Claude when it finishes a turn (no wrapper needed).
     */
    HOOKS,
}

/** What a session is waiting for from the owner while Away mode holds it. */
@Serializable
enum class AwaitingKind {
    /** A permission dialog: approve or deny. */
    PERMISSION,

    /** Claude finished its turn: send the next instruction, or let it stop. */
    REPLY,
}

@Serializable
enum class QuickAction { APPROVE, DENY, INTERRUPT, CONTINUE }

/** Keys the phone can press in a wrapper session's terminal. */
@Serializable
enum class TerminalKey { ENTER, ESCAPE, TAB, SHIFT_TAB, UP, DOWN, CTRL_C, DIGIT_1, DIGIT_2, DIGIT_3 }

@Serializable
enum class EventKind { SESSION_START, PROMPT, TOOL_USE, TOOL_RESULT, NOTIFICATION, MESSAGE, STOP, ERROR, SESSION_END }

@Serializable
enum class DeliveryResult {
    DELIVERED,

    /** Accepted, and handed to Claude when it next finishes a turn. */
    QUEUED,
    FAILED,
}

@Serializable
enum class ErrorCode {
    AUTH_FAILED,
    NOT_PAIRED,
    FORBIDDEN_PROJECT,
    READ_ONLY,
    SESSION_NOT_CONTROLLABLE,
    SESSION_NOT_FOUND,
    RATE_LIMITED,
    BAD_REQUEST,
    INTERNAL,
}
