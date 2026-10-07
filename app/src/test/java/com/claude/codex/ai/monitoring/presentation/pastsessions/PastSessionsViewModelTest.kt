package com.claude.codex.ai.monitoring.presentation.pastsessions

import com.claude.codex.ai.monitoring.core.utils.Clock
import com.claude.codex.ai.monitoring.core.utils.UiText
import com.claude.codex.ai.monitoring.domain.models.AgentSnapshotModel
import com.claude.codex.ai.monitoring.domain.models.ConnectionStatus
import com.claude.codex.ai.monitoring.domain.models.DeliveryStatus
import com.claude.codex.ai.monitoring.domain.models.PastSessionModel
import com.claude.codex.ai.monitoring.domain.models.SessionStatus
import com.claude.codex.ai.monitoring.fakes.FakeAgentRepository
import com.claude.codex.ai.monitoring.fakes.FakeHiddenSessionsRepository
import com.claude.codex.ai.monitoring.fakes.sessionModel
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PastSessionsViewModelTest {

    private val main = StandardTestDispatcher()
    private val repository = FakeAgentRepository(
        AgentSnapshotModel(connection = ConnectionStatus.Connected(1), hasSnapshot = true, canSendInput = true),
    )
    private val hidden = FakeHiddenSessionsRepository()
    private val old = PastSessionModel("c-old", "Fix the login bug", 100)
    private val open = PastSessionModel("c-open", "Refactor", 200)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun runCurrent() = main.scheduler.runCurrent()

    private fun viewModel() = PastSessionsViewModel("p1", "Alpha", repository, hidden, Clock { 1_000L })

    private fun PastSessionsViewModel.collectEffects(): MutableList<PastSessionsEffect> {
        val effects = mutableListOf<PastSessionsEffect>()
        CoroutineScope(main).launch { this@collectEffects.effects.collect { effects += it } }
        return effects
    }

    @Test
    fun `a saved conversation is resumed with the typed instruction and then opened`() {
        repository.past["p1"] = listOf(old)
        val vm = viewModel()
        val effects = vm.collectEffects()
        runCurrent()
        assertEquals(listOf("c-old"), vm.pastSessionsUiState.value.sessions.map { it.claudeSessionId })

        vm.onEvent(PastSessionsEvent.OnSessionClick("c-old"))
        vm.onEvent(PastSessionsEvent.OnResumeTextChange("  add tests  "))
        vm.onEvent(PastSessionsEvent.OnResumeConfirm)
        runCurrent()
        assertEquals(listOf(Triple("p1", "c-old", "add tests")), repository.resumes)
        assertEquals(listOf<PastSessionsEffect>(PastSessionsEffect.OpenSession("resumed")), effects)
        assertNull(vm.pastSessionsUiState.value.resumeTarget)
    }

    @Test
    fun `a conversation Claude still has open is opened instead of resumed`() {
        repository.past["p1"] = listOf(open)
        repository.snapshot.value = repository.snapshot.value.copy(
            sessions = listOf(sessionModel("wrapper-1", status = SessionStatus.IDLE).copy(claudeSessionId = "c-open")),
        )
        val vm = viewModel()
        val effects = vm.collectEffects()
        runCurrent()
        assertEquals("wrapper-1", vm.pastSessionsUiState.value.sessions.single().openSessionId)
        vm.onEvent(PastSessionsEvent.OnSessionClick("c-open"))
        runCurrent()
        assertEquals(listOf<PastSessionsEffect>(PastSessionsEffect.OpenSession("wrapper-1")), effects)
        assertTrue(repository.resumes.isEmpty())
    }

    @Test
    fun `a refused resume shows the computer's reason and keeps the dialog open`() {
        repository.past["p1"] = listOf(old)
        repository.nextDelivery = DeliveryStatus.Failed(DeliveryStatus.FailureReason.NOT_WAITING, "Claude is still open in this conversation")
        val vm = viewModel()
        runCurrent()
        vm.onEvent(PastSessionsEvent.OnSessionClick("c-old"))
        vm.onEvent(PastSessionsEvent.OnResumeTextChange("go"))
        vm.onEvent(PastSessionsEvent.OnResumeConfirm)
        runCurrent()
        assertEquals(UiText.Raw("Claude is still open in this conversation"), vm.pastSessionsUiState.value.resumeError)
        assertEquals("c-old", vm.pastSessionsUiState.value.resumeTarget?.claudeSessionId)
    }

    @Test
    fun `removing hides a conversation on this phone, and no answer shows as unavailable`() {
        repository.past["p1"] = listOf(old, open)
        val vm = viewModel()
        runCurrent()
        vm.onEvent(PastSessionsEvent.OnSessionLongClick("c-old"))
        vm.onEvent(PastSessionsEvent.OnRemoveConfirm)
        runCurrent()
        assertEquals(listOf("c-open"), vm.pastSessionsUiState.value.sessions.map { it.claudeSessionId })

        repository.past.remove("p1")
        vm.onEvent(PastSessionsEvent.OnRetryClick)
        runCurrent()
        assertTrue(vm.pastSessionsUiState.value.isUnavailable)
    }

    @Test
    fun `a new session or a saved conversation can be opened in a terminal on the computer`() {
        repository.past["p1"] = listOf(old)
        val vm = viewModel()
        val effects = vm.collectEffects()
        runCurrent()

        vm.onEvent(PastSessionsEvent.OnNewSessionClick)
        runCurrent()
        vm.onEvent(PastSessionsEvent.OnSessionClick("c-old"))
        vm.onEvent(PastSessionsEvent.OnResumeInTerminal)
        runCurrent()
        assertEquals(listOf<Pair<String, String?>>("p1" to null, "p1" to "c-old"), repository.terminalStarts.toList())
        assertEquals(2, effects.size)
        assertNull(vm.pastSessionsUiState.value.resumeTarget)
    }
}
