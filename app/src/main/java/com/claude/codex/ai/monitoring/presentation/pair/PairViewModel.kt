package com.claude.codex.ai.monitoring.presentation.pair

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.domain.models.PairingError
import com.claude.codex.ai.monitoring.domain.models.PairingOfferModel
import com.claude.codex.ai.monitoring.domain.repo.PairingRepository
import com.claude.codex.ai.monitoring.domain.usecase.PairDeviceUseCase
import com.claude.codex.ai.monitoring.domain.usecase.ParsePairingCodeUseCase
import com.claude.codex.ai.monitoring.protocol.AgentCrypto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PairViewModel(
    private val parsePairingCode: ParsePairingCodeUseCase,
    private val pairDevice: PairDeviceUseCase,
    private val pairingRepository: PairingRepository,
) : ViewModel() {

    private val _pairUiState = MutableStateFlow(PairUiState(deviceName = pairingRepository.defaultDeviceName()))
    val pairUiState: StateFlow<PairUiState> = _pairUiState.asStateFlow()

    private var offer: PairingOfferModel? = null
    private var confirmStep: PairStep.Confirm? = null

    fun onEvent(event: PairEvent) {
        when (event) {
            is PairEvent.OnCodeScanned -> if (_pairUiState.value.step == PairStep.Scan) accept(event.raw, fromCamera = true)
            PairEvent.OnToggleCodeEntry -> _pairUiState.update { it.copy(showCodeEntry = !it.showCodeEntry, codeError = null) }
            is PairEvent.OnCodeInputChange -> _pairUiState.update { it.copy(codeInput = event.value, codeError = null) }
            PairEvent.OnCodeSubmit -> accept(_pairUiState.value.codeInput, fromCamera = false)
            is PairEvent.OnDeviceNameChange -> _pairUiState.update { it.copy(deviceName = event.value, nameError = null) }
            PairEvent.OnSendRequest -> send()
            PairEvent.OnRetry -> {
                offer = null
                confirmStep = null
                _pairUiState.update { it.copy(step = PairStep.Scan, codeInput = "", codeError = null) }
            }
        }
    }

    private fun accept(raw: String, fromCamera: Boolean) {
        parsePairingCode(raw)
            .onSuccess { parsed ->
                offer = parsed
                viewModelScope.launch {
                    val step = PairStep.Confirm(
                        PairOfferUiModel(
                            computerName = parsed.computerName,
                            address = parsed.baseUrl.substringAfter("://"),
                            desktopKey = AgentCrypto.shortFingerprint(parsed.desktopFingerprint),
                            phoneKey = AgentCrypto.shortFingerprint(pairingRepository.deviceFingerprint()),
                        ),
                    )
                    confirmStep = step
                    _pairUiState.update { it.copy(step = step) }
                }
            }
            .onFailure { error ->
                // A random QR in front of the camera is ignored; a pasted wrong code gets an explanation.
                if (!fromCamera) _pairUiState.update { it.copy(codeError = error.toMessage()) }
            }
    }

    private fun send() {
        val current = offer ?: return
        val name = _pairUiState.value.deviceName
        _pairUiState.update { it.copy(step = PairStep.Waiting(current.computerName)) }
        viewModelScope.launch {
            pairDevice(current, name)
                .onSuccess { pairing ->
                    _pairUiState.update { it.copy(step = PairStep.Success(pairing.computerName, pairing.canSendInput)) }
                }
                .onFailure { error ->
                    val backToForm = confirmStep
                    _pairUiState.update { state ->
                        if (error is PairingError.InvalidName && backToForm != null) {
                            // Only the name needs fixing; keep the scanned code.
                            state.copy(step = backToForm, nameError = R.string.pair_error_name)
                        } else {
                            state.copy(step = PairStep.Failed(error.toMessage()))
                        }
                    }
                }
        }
    }

    private fun Throwable.toMessage(): Int = when (this) {
        is PairingError.InvalidCode -> R.string.pair_error_invalid_code
        is PairingError.InsecureAddress -> R.string.pair_error_insecure
        is PairingError.InvalidName -> R.string.pair_error_name
        is PairingError.Rejected -> R.string.pair_error_rejected
        is PairingError.Expired -> R.string.pair_error_expired
        is PairingError.TimedOut -> R.string.pair_error_timeout
        is PairingError.RateLimited -> R.string.pair_error_rate_limited
        is PairingError.DesktopMismatch -> R.string.pair_error_mismatch
        else -> R.string.pair_error_network
    }
}
