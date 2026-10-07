package com.claude.codex.ai.monitoring.presentation.computerprojects

import com.claude.codex.ai.monitoring.core.utils.Clock
import com.claude.codex.ai.monitoring.domain.models.AgentSnapshotModel
import com.claude.codex.ai.monitoring.domain.models.AvailableProjectModel
import com.claude.codex.ai.monitoring.domain.models.AvailableProjectsModel
import com.claude.codex.ai.monitoring.domain.models.ConnectionStatus
import com.claude.codex.ai.monitoring.domain.models.DeliveryStatus
import com.claude.codex.ai.monitoring.fakes.FakeAgentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ComputerProjectsViewModelTest {

    private val main = StandardTestDispatcher()
    private val repository = FakeAgentRepository(
        AgentSnapshotModel(connection = ConnectionStatus.Connected(1), hasSnapshot = true, canSendInput = true),
    )
    private val shop = AvailableProjectModel("p-shop", "ShopKart", "Work\\ShopKart", 12, 100, monitored = false)
    private val notes = AvailableProjectModel("p-notes", "Notes App", "Work\\notes-app", 3, 50, monitored = true)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun runCurrent() = main.scheduler.runCurrent()

    private fun viewModel() = ComputerProjectsViewModel(repository, Clock { 1_000L })

    private fun ComputerProjectsViewModel.collectEffects(): MutableList<ComputerProjectsEffect> {
        val effects = mutableListOf<ComputerProjectsEffect>()
        CoroutineScope(main).launch { this@collectEffects.effects.collect { effects += it } }
        return effects
    }

    @Test
    fun `a project not watched yet is added, then its history opens`() {
        repository.available = AvailableProjectsModel(allowed = true, projects = listOf(shop, notes))
        val vm = viewModel()
        val effects = vm.collectEffects()
        runCurrent()
        assertEquals(listOf("ShopKart", "Notes App"), vm.computerProjectsUiState.value.projects.map { it.name })

        vm.onEvent(ComputerProjectsEvent.OnProjectClick("p-shop"))
        runCurrent()
        assertEquals(listOf("p-shop"), repository.addedProjects)
        assertTrue(vm.computerProjectsUiState.value.projects.first().monitored)
        assertEquals(listOf<ComputerProjectsEffect>(ComputerProjectsEffect.OpenHistory("p-shop", "ShopKart")), effects)
    }

    @Test
    fun `a watched project opens its history without adding anything`() {
        repository.available = AvailableProjectsModel(allowed = true, projects = listOf(notes))
        val vm = viewModel()
        val effects = vm.collectEffects()
        runCurrent()
        vm.onEvent(ComputerProjectsEvent.OnProjectClick("p-notes"))
        runCurrent()
        assertTrue(repository.addedProjects.isEmpty())
        assertEquals(listOf<ComputerProjectsEffect>(ComputerProjectsEffect.OpenHistory("p-notes", "Notes App")), effects)
    }

    @Test
    fun `a refusal is shown, a limited phone is told why, and no answer is unavailable`() {
        repository.available = AvailableProjectsModel(allowed = true, projects = listOf(shop))
        repository.nextDelivery = DeliveryStatus.Failed(DeliveryStatus.FailureReason.REJECTED, "This project is no longer on this computer")
        val vm = viewModel()
        runCurrent()
        vm.onEvent(ComputerProjectsEvent.OnProjectClick("p-shop"))
        runCurrent()
        assertNotNull(vm.computerProjectsUiState.value.error)

        repository.available = AvailableProjectsModel(allowed = false, projects = emptyList())
        vm.onEvent(ComputerProjectsEvent.OnRetryClick)
        runCurrent()
        assertTrue(vm.computerProjectsUiState.value.notAllowed)

        repository.available = null
        vm.onEvent(ComputerProjectsEvent.OnRetryClick)
        runCurrent()
        assertTrue(vm.computerProjectsUiState.value.isUnavailable)
    }
}
