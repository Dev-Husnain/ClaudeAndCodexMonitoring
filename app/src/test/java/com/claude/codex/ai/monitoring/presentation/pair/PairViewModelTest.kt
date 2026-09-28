package com.claude.codex.ai.monitoring.presentation.pair

import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.utils.UiText
import com.claude.codex.ai.monitoring.domain.models.PairingError
import com.claude.codex.ai.monitoring.domain.usecase.PairDeviceUseCase
import com.claude.codex.ai.monitoring.domain.usecase.ParsePairingCodeUseCase
import com.claude.codex.ai.monitoring.fakes.FakePairingRepository
import com.claude.codex.ai.monitoring.protocol.PairingCode
import com.claude.codex.ai.monitoring.protocol.PairingOfferDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PairViewModelTest {

    private val main = StandardTestDispatcher()
    private val repository = FakePairingRepository()
    private val validCode = PairingCode.encode(PairingOfferDto(url = "https://agent.example", token = "t", fp = "a".repeat(64), name = "Laptop"))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun run() = main.scheduler.advanceUntilIdle()

    private fun viewModel() = PairViewModel(ParsePairingCodeUseCase(), PairDeviceUseCase(repository), repository)

    @Test
    fun `starts on scan with the default device name`() {
        val vm = viewModel()
        assertEquals(PairStep.Scan, vm.pairUiState.value.step)
        assertEquals("Test Phone", vm.pairUiState.value.deviceName)
    }

    @Test
    fun `scanning a valid code shows both keys to compare`() {
        val vm = viewModel()
        vm.onEvent(PairEvent.OnCodeScanned(validCode))
        run()
        val confirm = assertIs<PairStep.Confirm>(vm.pairUiState.value.step)
        assertEquals("Laptop", confirm.offer.computerName)
        assertEquals("aaaa aaaa aaaa aaaa", confirm.offer.desktopKey)
        assertEquals("ffff ffff ffff ffff", confirm.offer.phoneKey)
    }

    @Test
    fun `random qr codes are ignored but a pasted bad code explains why`() {
        val vm = viewModel()
        vm.onEvent(PairEvent.OnCodeScanned("https://example.com"))
        run()
        assertEquals(PairStep.Scan, vm.pairUiState.value.step)
        assertEquals(null, vm.pairUiState.value.codeError)

        vm.onEvent(PairEvent.OnCodeInputChange("nonsense"))
        vm.onEvent(PairEvent.OnCodeSubmit)
        assertEquals(R.string.pair_error_invalid_code, vm.pairUiState.value.codeError)
    }

    @Test
    fun `approval moves through waiting to success`() {
        val vm = viewModel()
        vm.onEvent(PairEvent.OnCodeScanned(validCode))
        run()
        vm.onEvent(PairEvent.OnDeviceNameChange("My Phone"))
        vm.onEvent(PairEvent.OnSendRequest)
        run()
        assertIs<PairStep.Waiting>(vm.pairUiState.value.step)
        assertEquals("My Phone", repository.requests.single().second)

        repository.nextResult.complete(Result.success(FakePairingRepository.pairing(canSendInput = true)))
        run()
        val success = assertIs<PairStep.Success>(vm.pairUiState.value.step)
        assertTrue(success.canSendInput)
    }

    @Test
    fun `a rejected request shows the reason and can start over`() {
        val vm = viewModel()
        vm.onEvent(PairEvent.OnCodeScanned(validCode))
        run()
        vm.onEvent(PairEvent.OnSendRequest)
        repository.nextResult.complete(Result.failure(PairingError.DesktopMismatch()))
        run()
        assertEquals(UiText.Res(R.string.pair_error_mismatch), assertIs<PairStep.Failed>(vm.pairUiState.value.step).message)

        vm.onEvent(PairEvent.OnRetry)
        assertEquals(PairStep.Scan, vm.pairUiState.value.step)
    }

    @Test
    fun `a blank name keeps the form and never contacts the computer`() {
        val vm = viewModel()
        vm.onEvent(PairEvent.OnCodeScanned(validCode))
        run()
        vm.onEvent(PairEvent.OnDeviceNameChange("   "))
        vm.onEvent(PairEvent.OnSendRequest)
        run()
        assertIs<PairStep.Confirm>(vm.pairUiState.value.step)
        assertEquals(R.string.pair_error_name, vm.pairUiState.value.nameError)
        assertTrue(repository.requests.isEmpty())
    }

    @Test
    fun `unreachable tunnel address says to use USB instead`() {
        val vm = viewModel()
        vm.onEvent(PairEvent.OnCodeScanned(validCode))
        run()
        vm.onEvent(PairEvent.OnSendRequest)
        repository.nextResult.complete(Result.failure(PairingError.Network()))
        run()
        val failed = assertIs<PairStep.Failed>(vm.pairUiState.value.step)
        assertEquals(UiText.Res(R.string.pair_error_network_remote, listOf("agent.example")), failed.message)
    }
}
