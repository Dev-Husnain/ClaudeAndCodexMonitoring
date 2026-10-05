package com.claude.codex.ai.monitoring.domain.models

/** Why a session needs the user. Alerts never carry prompt, tool or terminal text. */
enum class SessionAlertKind {
    /** Away mode holds a permission request for the phone. */
    PERMISSION,

    /** Claude finished a turn and Away mode waits for the next instruction. */
    REPLY,

    /** Claude waits at the computer: its own permission dialog, a question, or other input. */
    INPUT,

    /** Claude stopped because of an error. */
    ERROR,
}

data class SessionAlertModel(
    val sessionId: String,
    /** Null when the computer has not named the project; the alert then uses a generic name. */
    val projectName: String?,
    val kind: SessionAlertKind,
)
