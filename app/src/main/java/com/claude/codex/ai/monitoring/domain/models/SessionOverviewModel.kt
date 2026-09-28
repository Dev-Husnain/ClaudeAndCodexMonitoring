package com.claude.codex.ai.monitoring.domain.models

/** Home screen data: sessions needing attention first, then everything else grouped by project. */
data class SessionOverviewModel(
    val connection: ConnectionStatus,
    val computer: ComputerModel?,
    val needsYou: List<SessionModel>,
    val projects: List<ProjectSessionsModel>,
    val projectNames: Map<String, String>,
    val hasSnapshot: Boolean,
    val lastConnectedAtMs: Long?,
)
