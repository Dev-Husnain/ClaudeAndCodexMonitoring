package com.claude.codex.ai.monitoring.presentation.sessiondetail

import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.utils.Clock
import com.claude.codex.ai.monitoring.core.utils.UiText
import com.claude.codex.ai.monitoring.domain.models.AgentSnapshotModel
import com.claude.codex.ai.monitoring.domain.models.AwaitingKind
import com.claude.codex.ai.monitoring.domain.models.AwaitingModel
import com.claude.codex.ai.monitoring.domain.models.ConnectionStatus
import com.claude.codex.ai.monitoring.domain.models.DeliveryStatus
import com.claude.codex.ai.monitoring.domain.models.QuickActionType
import com.claude.codex.ai.monitoring.domain.models.SessionControl
import com.claude.codex.ai.monitoring.domain.models.SessionStatus
import com.claude.codex.ai.monitoring.domain.models.TerminalKeyType
import com.claude.codex.ai.monitoring.domain.models.TerminalLineModel
import com.claude.codex.ai.monitoring.domain.models.TerminalScreenModel
import com.claude.codex.ai.monitoring.domain.models.TerminalSpanModel
import com.claude.codex.ai.monitoring.domain.usecase.ObserveSessionDetailUseCase
import com.claude.codex.ai.monitoring.domain.usecase.SendInstructionUseCase
import com.claude.codex.ai.monitoring.fakes.FakeAgentRepository
import com.claude.codex.ai.monitoring.fakes.FakeSettingsRepository
import com.claude.codex.ai.monitoring.fakes.sessionModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

/** Main gets its own scheduler: the ViewModel runs an endless relative-time ticker (see CLAUDE.md). */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionDetailViewModelTest {

    private val main = StandardTestDispatcher()
    private val repository = FakeAgentRepository(
        AgentSnapshotModel(
            connection = ConnectionStatus.Connected(1),
            sessions = listOf(sessionModel("s1", status = SessionStatus.WAITING_INPUT).copy(awaiting = AwaitingModel(AwaitingKind.PERMISSION, "Bash: rm -rf build", 1))),
            hasSnapshot = true,
            canSendInput = true,
            awayMode = true,
        ),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun runCurrent() = main.scheduler.runCurrent()

    private fun viewModel() = SessionDetailViewModel(
        sessionId = "s1",
        observeSessionDetail = ObserveSessionDetailUseCase(repository),
        agentRepository = repository,
        sendInstruction = SendInstructionUseCase(repository),
        settingsRepository = FakeSettingsRepository(),
        clock = Clock { 1_000L },
    )

    @Test
    fun `a held permission request is shown with controls`() {
        val vm = viewModel()
        runCurrent()
        val state = vm.sessionDetailUiState.value
        assertTrue(state.canControl)
        assertTrue(state.showComposer)
        assertEquals(AwaitingUiModel(isPermission = true, detail = "Bash: rm -rf build"), state.awaiting)
    }

    @Test
    fun `approve is sent and confirmed`() {
        val vm = viewModel()
        runCurrent()
        vm.onEvent(SessionDetailEvent.OnQuickAction(QuickActionType.APPROVE))
        runCurrent()
        assertEquals(listOf("s1" to QuickActionType.APPROVE), repository.quickActions)
        assertEquals(UiText.Res(R.string.delivery_delivered), vm.sessionDetailUiState.value.deliveryNote)
        assertFalse(vm.sessionDetailUiState.value.sending)
    }

    @Test
    fun `a queued message clears the composer and says it is queued`() {
        repository.nextDelivery = DeliveryStatus.Queued
        val vm = viewModel()
        runCurrent()
        vm.onEvent(SessionDetailEvent.OnComposerChange("  run the tests  "))
        vm.onEvent(SessionDetailEvent.OnSendClick)
        runCurrent()
        assertEquals(listOf("s1" to "run the tests"), repository.instructions)
        assertEquals("", vm.sessionDetailUiState.value.composerText)
        assertEquals(UiText.Res(R.string.delivery_queued), vm.sessionDetailUiState.value.deliveryNote)
    }

    @Test
    fun `a failed message keeps the text so it can be retried`() {
        repository.nextDelivery = DeliveryStatus.Failed(DeliveryStatus.FailureReason.NOT_CONNECTED)
        val vm = viewModel()
        runCurrent()
        vm.onEvent(SessionDetailEvent.OnComposerChange("hello"))
        vm.onEvent(SessionDetailEvent.OnSendClick)
        runCurrent()
        assertEquals("hello", vm.sessionDetailUiState.value.composerText)
        assertEquals(UiText.Res(R.string.delivery_not_connected), vm.sessionDetailUiState.value.deliveryNote)
    }

    @Test
    fun `blank messages are never sent`() {
        val vm = viewModel()
        runCurrent()
        vm.onEvent(SessionDetailEvent.OnComposerChange("   "))
        vm.onEvent(SessionDetailEvent.OnSendClick)
        runCurrent()
        assertTrue(repository.instructions.isEmpty())
    }

    @Test
    fun `typing survives live updates from the computer`() {
        val vm = viewModel()
        runCurrent()
        vm.onEvent(SessionDetailEvent.OnComposerChange("half-typed"))
        repository.snapshot.value = repository.snapshot.value.copy(awayMode = false)
        runCurrent()
        assertEquals("half-typed", vm.sessionDetailUiState.value.composerText)
        assertFalse(vm.sessionDetailUiState.value.awayMode)
        assertNotNull(vm.sessionDetailUiState.value.header)
    }

    private fun wrapped() {
        repository.snapshot.value = repository.snapshot.value.copy(
            sessions = repository.snapshot.value.sessions.map { it.copy(control = SessionControl.WRAPPER, awaiting = null) },
        )
    }

    @Test
    fun `the terminal is attached only while its tab is open and on screen`() {
        wrapped()
        val vm = viewModel()
        val watcher = CoroutineScope(main).launch { vm.terminalUiState.collect {} }
        runCurrent()
        assertTrue(vm.sessionDetailUiState.value.hasTerminal)
        assertEquals(0, repository.attachedTerminals, "the Activity tab does not stream the terminal")

        vm.onEvent(SessionDetailEvent.OnTabSelect(DetailTab.TERMINAL))
        runCurrent()
        assertEquals(1, repository.attachedTerminals)
        val screen = TerminalScreenModel(80, listOf(TerminalLineModel(listOf(TerminalSpanModel("> ready")))))
        repository.terminalScreen.value = screen
        runCurrent()
        assertEquals(screen, vm.terminalUiState.value.screen)

        watcher.cancel()
        main.scheduler.advanceTimeBy(6_000)
        runCurrent()
        assertEquals(0, repository.attachedTerminals, "leaving the screen detaches after the grace period")
    }

    @Test
    fun `sessions without the wrapper have no terminal tab`() {
        val vm = viewModel()
        runCurrent()
        vm.onEvent(SessionDetailEvent.OnTabSelect(DetailTab.TERMINAL))
        runCurrent()
        assertFalse(vm.sessionDetailUiState.value.hasTerminal)
        assertEquals(DetailTab.ACTIVITY, vm.sessionDetailUiState.value.tab)
    }

    @Test
    fun `terminal keys are sent, and only a failure leaves a note`() {
        wrapped()
        val vm = viewModel()
        runCurrent()
        vm.onEvent(SessionDetailEvent.OnTerminalKey(TerminalKeyType.SHIFT_TAB))
        runCurrent()
        assertEquals(listOf("s1" to TerminalKeyType.SHIFT_TAB), repository.keys)
        assertEquals(null, vm.sessionDetailUiState.value.deliveryNote)

        repository.nextDelivery = DeliveryStatus.Failed(DeliveryStatus.FailureReason.READ_ONLY)
        vm.onEvent(SessionDetailEvent.OnTerminalKey(TerminalKeyType.ENTER))
        runCurrent()
        assertEquals(UiText.Res(R.string.delivery_read_only), vm.sessionDetailUiState.value.deliveryNote)
    }

    @Test
    fun `stop asks first, then stops Claude and says when it takes effect`() {
        repository.snapshot.value = repository.snapshot.value.copy(
            sessions = listOf(sessionModel("s1", status = SessionStatus.RUNNING)),
        )
        repository.nextDelivery = DeliveryStatus.Queued
        val vm = viewModel()
        runCurrent()
        assertTrue(vm.sessionDetailUiState.value.canStop)

        vm.onEvent(SessionDetailEvent.OnStopClick)
        assertTrue(vm.sessionDetailUiState.value.showStopConfirm)
        assertTrue(repository.quickActions.isEmpty(), "nothing is sent before confirming")

        vm.onEvent(SessionDetailEvent.OnStopConfirm)
        runCurrent()
        assertEquals(listOf("s1" to QuickActionType.INTERRUPT), repository.quickActions)
        assertFalse(vm.sessionDetailUiState.value.showStopConfirm)
        assertEquals(UiText.Res(R.string.delivery_stop_queued), vm.sessionDetailUiState.value.deliveryNote)
    }

    @Test
    fun `there is nothing to stop when Claude is idle`() {
        repository.snapshot.value = repository.snapshot.value.copy(sessions = listOf(sessionModel("s1", status = SessionStatus.IDLE)))
        val vm = viewModel()
        runCurrent()
        assertFalse(vm.sessionDetailUiState.value.canStop)
        vm.onEvent(SessionDetailEvent.OnStopClick)
        assertFalse(vm.sessionDetailUiState.value.showStopConfirm)
    }
}
