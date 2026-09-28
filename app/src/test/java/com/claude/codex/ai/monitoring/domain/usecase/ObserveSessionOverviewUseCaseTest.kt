package com.claude.codex.ai.monitoring.domain.usecase

import com.claude.codex.ai.monitoring.domain.models.AgentSnapshotModel
import com.claude.codex.ai.monitoring.domain.models.ProjectModel
import com.claude.codex.ai.monitoring.domain.models.SessionStatus
import com.claude.codex.ai.monitoring.fakes.FakeAgentRepository
import com.claude.codex.ai.monitoring.fakes.sessionModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveSessionOverviewUseCaseTest {

    @Test
    fun `waiting sessions are pinned and others grouped by most recent project`() = runTest {
        val repository = FakeAgentRepository(
            AgentSnapshotModel(
                projects = listOf(ProjectModel("p1", "Alpha"), ProjectModel("p2", "Beta")),
                sessions = listOf(
                    sessionModel("a-old", projectId = "p1", lastEventAtMs = 10),
                    sessionModel("b-new", projectId = "p2", lastEventAtMs = 50),
                    sessionModel("a-wait", projectId = "p1", status = SessionStatus.WAITING_INPUT, lastEventAtMs = 5),
                    sessionModel("a-new", projectId = "p1", lastEventAtMs = 30),
                ),
                hasSnapshot = true,
            ),
        )

        val overview = ObserveSessionOverviewUseCase(repository)().first()

        assertEquals(listOf("a-wait"), overview.needsYou.map { it.sessionId })
        assertEquals(listOf("Beta", "Alpha"), overview.projects.map { it.project.name })
        assertEquals(listOf("a-new", "a-old"), overview.projects[1].sessions.map { it.sessionId })
    }

    @Test
    fun `sessions of an unknown project still appear under the project id`() = runTest {
        val repository = FakeAgentRepository(
            AgentSnapshotModel(sessions = listOf(sessionModel("s1", projectId = "ghost")), hasSnapshot = true),
        )
        val overview = ObserveSessionOverviewUseCase(repository)().first()
        assertEquals("ghost", overview.projects.single().project.name)
    }
}
