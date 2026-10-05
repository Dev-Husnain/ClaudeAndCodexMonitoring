package com.claude.codex.ai.monitoring.presentation.home

import com.claude.codex.ai.monitoring.core.utils.Clock
import com.claude.codex.ai.monitoring.domain.models.AgentSnapshotModel
import com.claude.codex.ai.monitoring.domain.models.ConnectionStatus
import com.claude.codex.ai.monitoring.domain.models.ProjectModel
import com.claude.codex.ai.monitoring.domain.models.SessionStatus
import com.claude.codex.ai.monitoring.domain.usecase.ObserveSessionOverviewUseCase
import com.claude.codex.ai.monitoring.fakes.FakeAgentRepository
import com.claude.codex.ai.monitoring.fakes.FakeHiddenSessionsRepository
import com.claude.codex.ai.monitoring.fakes.FakePairingRepository
import com.claude.codex.ai.monitoring.domain.models.AuthProblem
import com.claude.codex.ai.monitoring.fakes.sessionModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The ViewModel runs an endless relative-time ticker, so Main gets its own scheduler that the
 * test advances explicitly with [runCurrent] instead of draining it to idle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val main = StandardTestDispatcher()
    private val repository = FakeAgentRepository()
    private val pairing = FakePairingRepository(FakePairingRepository.pairing(computerName = "Laptop"))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun runCurrent() = main.scheduler.runCurrent()

    private val hidden = FakeHiddenSessionsRepository()

    private fun viewModel() = HomeViewModel(ObserveSessionOverviewUseCase(repository, hidden), repository, pairing, hidden, Clock { 1_000_000L })

    @Test
    fun `an ended session can be removed from the phone after confirming, a running one cannot`() {
        repository.snapshot.value = AgentSnapshotModel(
            connection = ConnectionStatus.Connected(1),
            projects = listOf(ProjectModel("p1", "Alpha")),
            sessions = listOf(sessionModel("done", status = SessionStatus.ENDED, lastEventAtMs = 500), sessionModel("busy")),
            hasSnapshot = true,
        )
        val vm = viewModel()
        runCurrent()

        vm.onEvent(HomeEvent.OnRemoveRequest("busy"))
        assertEquals(null, vm.homeUiState.value.removeTarget)

        vm.onEvent(HomeEvent.OnRemoveRequest("done"))
        assertEquals("done", vm.homeUiState.value.removeTarget?.sessionId)
        vm.onEvent(HomeEvent.OnRemoveConfirm)
        runCurrent()
        // Stored with the computer's activity time, so a clock difference cannot undo the removal.
        // The project keeps another session, so only this one goes.
        assertEquals(mapOf("done" to 500L), hidden.hidden.value)
        assertEquals(listOf("busy"), vm.homeUiState.value.projects.single().sessions.map { it.sessionId })
    }

    @Test
    fun `removing a project's last session also removes its conversation from History and the project heading`() {
        repository.snapshot.value = AgentSnapshotModel(
            connection = ConnectionStatus.Connected(1),
            projects = listOf(ProjectModel("p1", "Alpha"), ProjectModel("p2", "Beta")),
            sessions = listOf(
                sessionModel("wrapper-1", projectId = "p2", status = SessionStatus.ENDED, lastEventAtMs = 700).copy(claudeSessionId = "c-1"),
                sessionModel("busy"),
            ),
            hasSnapshot = true,
        )
        val vm = viewModel()
        runCurrent()

        vm.onEvent(HomeEvent.OnRemoveRequest("wrapper-1"))
        vm.onEvent(HomeEvent.OnRemoveConfirm)
        runCurrent()
        assertEquals(mapOf("wrapper-1" to 700L, "c-1" to 700L, "project:p2" to 700L), hidden.hidden.value)
        assertEquals(listOf("Alpha"), vm.homeUiState.value.projects.map { it.name })
    }

    @Test
    fun `a project without live sessions can be removed from the phone`() {
        repository.snapshot.value = AgentSnapshotModel(
            connection = ConnectionStatus.Connected(1),
            projects = listOf(ProjectModel("p1", "Alpha"), ProjectModel("p2", "Beta")),
            sessions = listOf(sessionModel("busy")),
            hasSnapshot = true,
        )
        val vm = viewModel()
        runCurrent()

        vm.onEvent(HomeEvent.OnRemoveProjectRequest("p1"))
        assertEquals(null, vm.homeUiState.value.removeProjectTarget, "a project with a live session stays")
        vm.onEvent(HomeEvent.OnRemoveProjectRequest("p2"))
        vm.onEvent(HomeEvent.OnRemoveProjectConfirm)
        runCurrent()
        assertEquals(listOf("Alpha"), vm.homeUiState.value.projects.map { it.name })
    }

    @Test
    fun `shows loading until the first snapshot`() {
        val vm = viewModel()
        runCurrent()
        assertTrue(vm.homeUiState.value.isLoading)
    }

    @Test
    fun `shows the full offline state when unreachable with nothing cached`() {
        repository.snapshot.value = AgentSnapshotModel(connection = ConnectionStatus.Offline(null))
        val vm = viewModel()
        runCurrent()
        with(vm.homeUiState.value) {
            assertTrue(isOffline)
            assertFalse(isLoading)
        }
    }

    @Test
    fun `keeps cached sessions with a stale notice when the computer goes offline`() {
        repository.snapshot.value = AgentSnapshotModel(
            connection = ConnectionStatus.Offline(lastConnectedAtMs = 900_000L),
            projects = listOf(ProjectModel("p1", "App")),
            sessions = listOf(sessionModel("s1", status = SessionStatus.WAITING_INPUT)),
            hasSnapshot = true,
            lastConnectedAtMs = 900_000L,
        )
        val vm = viewModel()
        runCurrent()
        with(vm.homeUiState.value) {
            assertFalse(isOffline)
            assertNotNull(staleNotice)
            assertEquals(listOf("s1"), needsYou.map { it.sessionId })
        }
    }

    @Test
    fun `connected with no sessions shows the empty state`() {
        repository.snapshot.value = AgentSnapshotModel(connection = ConnectionStatus.Connected(1), hasSnapshot = true)
        val vm = viewModel()
        runCurrent()
        assertTrue(vm.homeUiState.value.isEmpty)
    }

    @Test
    fun `live updates flow into the state`() {
        val vm = viewModel()
        runCurrent()
        repository.snapshot.value = AgentSnapshotModel(
            connection = ConnectionStatus.Connected(1),
            sessions = listOf(sessionModel("s1")),
            hasSnapshot = true,
        )
        runCurrent()
        assertEquals(listOf("s1"), vm.homeUiState.value.projects.single().sessions.map { it.sessionId })
    }

    @Test
    fun `retry skips the back-off`() {
        val vm = viewModel()
        vm.onEvent(HomeEvent.OnRetryClick)
        assertEquals(1, repository.reconnectCalls)
    }

    @Test
    fun `revoked phone gets a pair again state instead of data`() {
        repository.snapshot.value = AgentSnapshotModel(connection = ConnectionStatus.Unauthorized(AuthProblem.REVOKED), hasSnapshot = true)
        val vm = viewModel()
        runCurrent()
        with(vm.homeUiState.value) {
            assertEquals(com.claude.codex.ai.monitoring.R.string.home_unauthorized_revoked_title, unauthorized?.title)
            assertFalse(isLoading)
            assertFalse(isEmpty)
        }
        vm.onEvent(HomeEvent.OnPairAgainClick)
        runCurrent()
        assertEquals(1, pairing.unpairCalls)
    }

    @Test
    fun `read-only grant is surfaced`() {
        repository.snapshot.value = AgentSnapshotModel(connection = ConnectionStatus.Connected(1), hasSnapshot = true, canSendInput = false)
        val vm = viewModel()
        runCurrent()
        assertTrue(vm.homeUiState.value.readOnly)
        assertEquals("Laptop", vm.homeUiState.value.computerName)
    }
}
