package com.claude.codex.ai.monitoring.presentation.home

import com.claude.codex.ai.monitoring.core.utils.Clock
import com.claude.codex.ai.monitoring.domain.models.AgentSnapshotModel
import com.claude.codex.ai.monitoring.domain.models.ConnectionStatus
import com.claude.codex.ai.monitoring.domain.models.ProjectModel
import com.claude.codex.ai.monitoring.domain.models.SessionStatus
import com.claude.codex.ai.monitoring.domain.usecase.ObserveSessionOverviewUseCase
import com.claude.codex.ai.monitoring.fakes.FakeAgentRepository
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

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun runCurrent() = main.scheduler.runCurrent()

    private fun viewModel() = HomeViewModel(ObserveSessionOverviewUseCase(repository), repository, Clock { 1_000_000L })

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
}
