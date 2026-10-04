package com.claude.codex.ai.monitoring.domain.usecase

import com.claude.codex.ai.monitoring.domain.models.AgentSnapshotModel
import com.claude.codex.ai.monitoring.domain.models.ProjectModel
import com.claude.codex.ai.monitoring.domain.models.ProjectSessionsModel
import com.claude.codex.ai.monitoring.domain.models.SessionOverviewModel
import com.claude.codex.ai.monitoring.domain.models.SessionStatus
import com.claude.codex.ai.monitoring.domain.repo.AgentRepository
import com.claude.codex.ai.monitoring.domain.repo.HiddenSessionsRepository
import com.claude.codex.ai.monitoring.domain.repo.hides
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    private val hiddenSessionsRepository: HiddenSessionsRepository,
) {
    operator fun invoke(): Flow<SessionOverviewModel> =
        combine(agentRepository.snapshot, hiddenSessionsRepository.hidden) { snapshot, hidden -> snapshot.toOverview(hidden) }
            .distinctUntilChanged()

    private fun AgentSnapshotModel.toOverview(hidden: Map<String, Long>): SessionOverviewModel {
        val projectsById = projects.associateBy { it.projectId }
        val (needsYou, others) = sessions
            // Removed from this phone, unless Claude worked in it since.
            .filterNot { hidden.hides(it.sessionId, it.lastEventAtMs) }
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
            .plus(
                projects.filter { project -> others.none { it.projectId == project.projectId } }
                    .map { ProjectSessionsModel(project = it, sessions = emptyList()) },
            )
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
