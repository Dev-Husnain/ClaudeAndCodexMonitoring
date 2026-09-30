package com.claude.codex.ai.monitoring.domain.repo

import com.claude.codex.ai.monitoring.domain.models.AgentSnapshotModel
import com.claude.codex.ai.monitoring.domain.models.DeliveryStatus
import com.claude.codex.ai.monitoring.domain.models.QuickActionType
import com.claude.codex.ai.monitoring.domain.models.TerminalKeyType
import com.claude.codex.ai.monitoring.domain.models.TerminalScreenModel
import kotlinx.coroutines.flow.Flow

interface AgentRepository {
    /** Live state of the paired computer. Collecting it keeps the connection open. */
    val snapshot: Flow<AgentSnapshotModel>

    /** Skips the current back-off delay and reconnects immediately. */
    fun reconnectNow()

    /** Asks the computer for the recent timeline of a session; the result arrives through [snapshot]. */
    fun requestHistory(sessionId: String)

    /** Sends an instruction; the result is the computer's confirmation. */
    suspend fun sendInstruction(sessionId: String, text: String): DeliveryStatus

    suspend fun quickAction(sessionId: String, action: QuickActionType): DeliveryStatus

    suspend fun setAwayMode(enabled: Boolean): DeliveryStatus

    /**
     * The terminal of a wrapper session, while collected: collecting attaches it on the computer (also
     * after a reconnect), and cancelling detaches it. Emits null until the first screen arrives.
     */
    fun terminal(sessionId: String): Flow<TerminalScreenModel?>

    suspend fun pressKey(sessionId: String, key: TerminalKeyType): DeliveryStatus
}
