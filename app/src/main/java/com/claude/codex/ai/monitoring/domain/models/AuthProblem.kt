package com.claude.codex.ai.monitoring.domain.models

enum class AuthProblem {
    /** The computer does not know this phone (for example, it was reset). */
    NOT_PAIRED,

    /** The owner revoked this phone on the computer. */
    REVOKED,

    /** The computer rejected this phone's signature. */
    AUTH_FAILED,

    /** Whoever answered could not prove it holds the pinned desktop key. */
    DESKTOP_MISMATCH,
}
