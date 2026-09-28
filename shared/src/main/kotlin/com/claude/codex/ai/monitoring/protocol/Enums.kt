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
}

@Serializable
enum class QuickAction { APPROVE, DENY, INTERRUPT, CONTINUE }

@Serializable
enum class EventKind { SESSION_START, PROMPT, TOOL_USE, TOOL_RESULT, NOTIFICATION, MESSAGE, STOP, ERROR, SESSION_END }

@Serializable
enum class DeliveryResult { DELIVERED, FAILED }

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
