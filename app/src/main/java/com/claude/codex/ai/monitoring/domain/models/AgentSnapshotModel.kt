package com.claude.codex.ai.monitoring.domain.models

/** Everything the phone currently knows about the paired computer. */
data class AgentSnapshotModel(
    val connection: ConnectionStatus = ConnectionStatus.Connecting,
    val computer: ComputerModel? = null,
    val projects: List<ProjectModel> = emptyList(),
    val sessions: List<SessionModel> = emptyList(),
    /** Per session, oldest first. A missing key means history was never loaded. */
    val timelines: Map<String, List<TimelineEventModel>> = emptyMap(),
    /** True once a `ready` snapshot has been received at least once. */
    val hasSnapshot: Boolean = false,
    val lastConnectedAtMs: Long? = null,
    /** From the owner's grant; false means read-only. */
    val canSendInput: Boolean = false,
)
