package com.claude.codex.ai.monitoring.domain.repo

import com.claude.codex.ai.monitoring.domain.models.AgentSnapshotModel
import kotlinx.coroutines.flow.Flow

interface AgentRepository {
    /** Live state of the paired computer. Collecting it keeps the connection open. */
    val snapshot: Flow<AgentSnapshotModel>

    /** Skips the current back-off delay and reconnects immediately. */
    fun reconnectNow()

    /** Asks the computer for the recent timeline of a session; the result arrives through [snapshot]. */
    fun requestHistory(sessionId: String)
}
