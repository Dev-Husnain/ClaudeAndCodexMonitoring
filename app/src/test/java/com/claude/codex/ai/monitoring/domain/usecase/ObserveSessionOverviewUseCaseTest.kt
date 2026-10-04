package com.claude.codex.ai.monitoring.domain.usecase

import com.claude.codex.ai.monitoring.domain.models.AgentSnapshotModel
import com.claude.codex.ai.monitoring.domain.models.ProjectModel
import com.claude.codex.ai.monitoring.domain.models.SessionStatus
import com.claude.codex.ai.monitoring.fakes.FakeAgentRepository
import com.claude.codex.ai.monitoring.fakes.FakeHiddenSessionsRepository
import com.claude.codex.ai.monitoring.fakes.sessionModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObserveSessionOverviewUseCaseTest {

    private val hidden = FakeHiddenSessionsRepository()

    @Test
    fun `a session removed from the phone stays hidden until Claude works in it again`() = runTest {
        val repository = FakeAgentRepository(
            AgentSnapshotModel(
                projects = listOf(ProjectModel("p1", "Alpha")),
                sessions = listOf(sessionModel("s1", status = SessionStatus.ENDED, lastEventAtMs = 100), sessionModel("s2", lastEventAtMs = 90)),
                hasSnapshot = true,
            ),
        )
        hidden.hide("s1", atMs = 100)
        assertEquals(listOf("s2"), ObserveSessionOverviewUseCase(repository, hidden)().first().projects.single().sessions.map { it.sessionId })

        repository.snapshot.value = repository.snapshot.value.copy(
            sessions = listOf(sessionModel("s1", status = SessionStatus.RUNNING, lastEventAtMs = 200), sessionModel("s2", lastEventAtMs = 90)),
        )
        assertEquals(listOf("s1", "s2"), ObserveSessionOverviewUseCase(repository, hidden)().first().projects.single().sessions.map { it.sessionId })
    }

    @Test
    fun `monitored projects without sessions still get a section, for their history`() = runTest {
        val repository = FakeAgentRepository(
            AgentSnapshotModel(projects = listOf(ProjectModel("p1", "Alpha"), ProjectModel("p2", "Beta")), sessions = listOf(sessionModel("s1")), hasSnapshot = true),
        )
        val overview = ObserveSessionOverviewUseCase(repository, hidden)().first()
        assertEquals(listOf("Alpha", "Beta"), overview.projects.map { it.project.name })
        assertTrue(overview.projects[1].sessions.isEmpty())
    }

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

        val overview = ObserveSessionOverviewUseCase(repository, hidden)().first()

        assertEquals(listOf("a-wait"), overview.needsYou.map { it.sessionId })
        assertEquals(listOf("Beta", "Alpha"), overview.projects.map { it.project.name })
        assertEquals(listOf("a-new", "a-old"), overview.projects[1].sessions.map { it.sessionId })
    }

    @Test
    fun `sessions of an unknown project still appear under the project id`() = runTest {
        val repository = FakeAgentRepository(
            AgentSnapshotModel(sessions = listOf(sessionModel("s1", projectId = "ghost")), hasSnapshot = true),
        )
        val overview = ObserveSessionOverviewUseCase(repository, hidden)().first()
        assertEquals("ghost", overview.projects.single().project.name)
    }
}
