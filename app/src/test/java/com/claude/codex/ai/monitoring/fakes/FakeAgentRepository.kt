package com.claude.codex.ai.monitoring.fakes

import com.claude.codex.ai.monitoring.domain.models.AgentSnapshotModel
import com.claude.codex.ai.monitoring.domain.models.DeliveryStatus
import com.claude.codex.ai.monitoring.domain.models.QuickActionType
import com.claude.codex.ai.monitoring.domain.repo.AgentRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAgentRepository(initial: AgentSnapshotModel = AgentSnapshotModel()) : AgentRepository {
    override val snapshot = MutableStateFlow(initial)
    var reconnectCalls = 0
    val historyRequests = mutableListOf<String>()
    val instructions = mutableListOf<Pair<String, String>>()
    val quickActions = mutableListOf<Pair<String, QuickActionType>>()
    val awayModeRequests = mutableListOf<Boolean>()

    /** What the "computer" answers to every request. */
    var nextDelivery: DeliveryStatus = DeliveryStatus.Delivered

    override fun reconnectNow() {
        reconnectCalls++
    }

    override fun requestHistory(sessionId: String) {
        historyRequests += sessionId
    }

    override suspend fun sendInstruction(sessionId: String, text: String): DeliveryStatus {
        instructions += sessionId to text
        return nextDelivery
    }

    override suspend fun quickAction(sessionId: String, action: QuickActionType): DeliveryStatus {
        quickActions += sessionId to action
        return nextDelivery
    }

    override suspend fun setAwayMode(enabled: Boolean): DeliveryStatus {
        awayModeRequests += enabled
        return nextDelivery
    }
}
