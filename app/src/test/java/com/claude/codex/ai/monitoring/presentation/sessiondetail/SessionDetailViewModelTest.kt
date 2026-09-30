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
import com.claude.codex.ai.monitoring.domain.models.SessionStatus
import com.claude.codex.ai.monitoring.domain.usecase.ObserveSessionDetailUseCase
import com.claude.codex.ai.monitoring.domain.usecase.SendInstructionUseCase
import com.claude.codex.ai.monitoring.fakes.FakeAgentRepository
import com.claude.codex.ai.monitoring.fakes.FakeSettingsRepository
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
}
