package com.claude.codex.ai.monitoring.domain.models

/** A folder Claude Code worked in on the computer ("Projects on this computer"). */
data class AvailableProjectModel(
    val projectId: String,
    val name: String,
    /** The last two parts of its path, e.g. `OtherProjects\ShopKart`. */
    val pathHint: String,
    val conversations: Int,
    val lastActiveAtMs: Long,
    /** AgentMon already watches it. */
    val monitored: Boolean,
)

/** The computer's answer; [allowed] is false for a phone limited to chosen projects. */
data class AvailableProjectsModel(
    val allowed: Boolean,
    val projects: List<AvailableProjectModel>,
)
