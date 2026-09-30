package com.claude.codex.ai.monitoring.domain.usecase

import com.claude.codex.ai.monitoring.domain.models.AgentSnapshotModel
import com.claude.codex.ai.monitoring.domain.models.ProjectModel
import com.claude.codex.ai.monitoring.domain.models.ProjectSessionsModel
import com.claude.codex.ai.monitoring.domain.models.SessionOverviewModel
import com.claude.codex.ai.monitoring.domain.models.SessionStatus
import com.claude.codex.ai.monitoring.domain.repo.AgentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Builds the home overview: sessions waiting for input (or held by Away mode for an answer) are
 * pinned in "Needs you"; every other
 * session is grouped under its project. Projects with the most recent activity come first, and
 * sessions within a project are newest first.
 */
class ObserveSessionOverviewUseCase(
    private val agentRepository: AgentRepository,
) {
    operator fun invoke(): Flow<SessionOverviewModel> =
        agentRepository.snapshot.map { it.toOverview() }.distinctUntilChanged()

    private fun AgentSnapshotModel.toOverview(): SessionOverviewModel {
        val projectsById = projects.associateBy { it.projectId }
        val (needsYou, others) = sessions
            .sortedByDescending { it.lastEventAtMs }
            .partition { it.status == SessionStatus.WAITING_INPUT || it.awaiting != null }
        val grouped = others
            .groupBy { it.projectId }
            .map { (projectId, sessions) ->
                ProjectSessionsModel(
                    project = projectsById[projectId] ?: ProjectModel(projectId, projectId),
                    sessions = sessions,
                )
            }
            .sortedByDescending { group -> group.sessions.first().lastEventAtMs }
        return SessionOverviewModel(
            connection = connection,
            computer = computer,
            needsYou = needsYou,
            projects = grouped,
            projectNames = projects.associate { it.projectId to it.name },
            hasSnapshot = hasSnapshot,
            lastConnectedAtMs = lastConnectedAtMs,
            canSendInput = canSendInput,
            awayMode = awayMode,
        )
    }
}
