package com.claude.codex.ai.monitoring.domain.usecase

import com.claude.codex.ai.monitoring.domain.models.AgentSnapshotModel
import com.claude.codex.ai.monitoring.domain.models.AwaitingKind
import com.claude.codex.ai.monitoring.domain.models.AwaitingModel
import com.claude.codex.ai.monitoring.domain.models.ProjectModel
import com.claude.codex.ai.monitoring.domain.models.SessionAlertKind
import com.claude.codex.ai.monitoring.domain.models.SessionAlertModel
import com.claude.codex.ai.monitoring.domain.models.SessionModel
import com.claude.codex.ai.monitoring.domain.models.SessionStatus
import com.claude.codex.ai.monitoring.fakes.sessionModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DetectSessionAlertsUseCaseTest {

    private val detect = DetectSessionAlertsUseCase()

    private fun snapshot(vararg sessions: SessionModel) = AgentSnapshotModel(
        hasSnapshot = true,
        projects = listOf(ProjectModel("p1", "Alpha")),
        sessions = sessions.toList(),
    )

    private val running = sessionModel("s1", status = SessionStatus.RUNNING)
    private val held = running.copy(status = SessionStatus.WAITING_INPUT, awaiting = AwaitingModel(AwaitingKind.PERMISSION, "Bash: rm", 1))

    @Test
    fun `a session that starts needing the user raises one alert with only the project name`() {
        val changes = detect(snapshot(running), snapshot(held))
        assertEquals(listOf(SessionAlertModel("s1", "Alpha", SessionAlertKind.PERMISSION)), changes.raised)
        assertTrue(detect(snapshot(held), snapshot(held)).raised.isEmpty(), "no repeat while nothing changes")
    }

    @Test
    fun `a new reason alerts again, and answering clears the alert`() {
        val finished = running.copy(status = SessionStatus.IDLE, awaiting = AwaitingModel(AwaitingKind.REPLY, "done", 2))
        assertEquals(SessionAlertKind.REPLY, detect(snapshot(held), snapshot(finished)).raised.single().kind)
        assertEquals(setOf("s1"), detect(snapshot(finished), snapshot(running)).cleared)
    }

    @Test
    fun `errors and plain waiting alert, ended sessions clear`() {
        assertEquals(SessionAlertKind.ERROR, detect(snapshot(running), snapshot(running.copy(status = SessionStatus.ERROR))).raised.single().kind)
        val waiting = running.copy(status = SessionStatus.WAITING_INPUT)
        assertEquals(SessionAlertKind.INPUT, detect(snapshot(running), snapshot(waiting)).raised.single().kind)
        assertEquals(setOf("s1"), detect(snapshot(waiting), snapshot()).cleared)
    }

    @Test
    fun `the first snapshot does not replay what is already waiting`() {
        assertTrue(detect(null, snapshot(held)).raised.isEmpty())
        assertTrue(detect(AgentSnapshotModel(), snapshot(held)).raised.isEmpty())
        assertTrue(detect(snapshot(held), AgentSnapshotModel()).raised.isEmpty())
    }
}
