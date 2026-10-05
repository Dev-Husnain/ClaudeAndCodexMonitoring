package com.claude.codex.ai.monitoring.domain.models

data class ProjectSessionsModel(
    val project: ProjectModel,
    val sessions: List<SessionModel>,
    /** The computer's time of the newest activity in this project, also in sessions removed from the phone. */
    val lastActivityMs: Long = 0L,
)
