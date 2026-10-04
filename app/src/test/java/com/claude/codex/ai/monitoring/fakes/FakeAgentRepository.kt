package com.claude.codex.ai.monitoring.fakes

import com.claude.codex.ai.monitoring.domain.models.AgentSnapshotModel
import com.claude.codex.ai.monitoring.domain.models.DeliveryStatus
import com.claude.codex.ai.monitoring.domain.models.PastSessionModel
import com.claude.codex.ai.monitoring.domain.models.QuickActionType
import com.claude.codex.ai.monitoring.domain.models.ResumeOutcomeModel
import com.claude.codex.ai.monitoring.domain.models.TerminalKeyType
import com.claude.codex.ai.monitoring.domain.models.TerminalScreenModel
import com.claude.codex.ai.monitoring.domain.repo.AgentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

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

    /** The screen of the attached terminal; [attachedTerminals] counts how many collectors are attached. */
    val terminalScreen = MutableStateFlow<TerminalScreenModel?>(null)
    var attachedTerminals = 0
        private set
    val keys = mutableListOf<Pair<String, TerminalKeyType>>()

    override fun terminal(sessionId: String): Flow<TerminalScreenModel?> =
        terminalScreen.onStart { attachedTerminals++ }.onCompletion { attachedTerminals-- }

    override suspend fun pressKey(sessionId: String, key: TerminalKeyType): DeliveryStatus {
        keys += sessionId to key
        return nextDelivery
    }

    /** Saved conversations per project; a missing project answers null (computer unreachable). */
    val past = mutableMapOf<String, List<PastSessionModel>>()
    val resumes = mutableListOf<Triple<String, String, String>>()
    var nextResumeSessionId: String? = "resumed"

    override suspend fun pastSessions(projectId: String): List<PastSessionModel>? = past[projectId]

    override suspend fun resumeSession(projectId: String, claudeSessionId: String, text: String): ResumeOutcomeModel {
        resumes += Triple(projectId, claudeSessionId, text)
        return ResumeOutcomeModel(nextDelivery, nextResumeSessionId.takeIf { nextDelivery !is DeliveryStatus.Failed })
    }
}
