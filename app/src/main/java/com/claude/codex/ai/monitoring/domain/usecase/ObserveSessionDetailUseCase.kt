package com.claude.codex.ai.monitoring.domain.usecase

import com.claude.codex.ai.monitoring.domain.models.ConnectionStatus
import com.claude.codex.ai.monitoring.domain.models.SessionDetailModel
import com.claude.codex.ai.monitoring.domain.repo.AgentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Observes one session. Every time the connection (re)reaches Connected, the history of the
 * session is requested again so the timeline catches up with anything missed while offline.
 */
class ObserveSessionDetailUseCase(
    private val agentRepository: AgentRepository,
) {
    operator fun invoke(sessionId: String): Flow<SessionDetailModel> = channelFlow {
        launch {
            agentRepository.snapshot
                .map { it.connection is ConnectionStatus.Connected }
                .distinctUntilChanged()
                .filter { connected -> connected }
                .collect { agentRepository.requestHistory(sessionId) }
        }
        agentRepository.snapshot
            .map { snapshot ->
                val session = snapshot.sessions.firstOrNull { it.sessionId == sessionId }
                SessionDetailModel(
                    connection = snapshot.connection,
                    hasSnapshot = snapshot.hasSnapshot,
                    session = session,
                    projectName = session?.let { s ->
                        snapshot.projects.firstOrNull { it.projectId == s.projectId }?.name
                    },
                    timeline = snapshot.timelines[sessionId],
                )
            }
            .distinctUntilChanged()
            .collect { send(it) }
    }
}
