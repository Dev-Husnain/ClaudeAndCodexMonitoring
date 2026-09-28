package com.claude.codex.ai.monitoring.domain.models

data class ProjectSessionsModel(
    val project: ProjectModel,
    val sessions: List<SessionModel>,
)
