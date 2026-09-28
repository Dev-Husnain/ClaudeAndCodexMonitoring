package com.claude.codex.ai.monitoring.fakes

import com.claude.codex.ai.monitoring.domain.models.AgentSnapshotModel
import com.claude.codex.ai.monitoring.domain.repo.AgentRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAgentRepository(initial: AgentSnapshotModel = AgentSnapshotModel()) : AgentRepository {
    override val snapshot = MutableStateFlow(initial)
    var reconnectCalls = 0
    val historyRequests = mutableListOf<String>()

    override fun reconnectNow() {
        reconnectCalls++
    }

    override fun requestHistory(sessionId: String) {
        historyRequests += sessionId
    }
}
