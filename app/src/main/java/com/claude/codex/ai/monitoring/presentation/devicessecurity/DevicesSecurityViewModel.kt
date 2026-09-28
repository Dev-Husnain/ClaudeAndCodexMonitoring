package com.claude.codex.ai.monitoring.presentation.devicessecurity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.domain.repo.AgentRepository
import com.claude.codex.ai.monitoring.domain.repo.PairingRepository
import com.claude.codex.ai.monitoring.protocol.AgentCrypto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class DevicesSecurityViewModel(
    private val pairingRepository: PairingRepository,
    agentRepository: AgentRepository,
) : ViewModel() {

    private val _devicesSecurityUiState = MutableStateFlow(DevicesSecurityUiState())
    val devicesSecurityUiState: StateFlow<DevicesSecurityUiState> = _devicesSecurityUiState.asStateFlow()

    init {
        viewModelScope.launch {
            val phoneKey = AgentCrypto.shortFingerprint(pairingRepository.deviceFingerprint())
            // The live grant (from `ready`) wins over the one recorded at pairing time.
            combine(pairingRepository.pairing.filterNotNull(), agentRepository.snapshot) { pairing, snapshot ->
                val canSendInput = if (snapshot.hasSnapshot) snapshot.canSendInput else pairing.canSendInput
                _devicesSecurityUiState.value.copy(
                    computerName = pairing.computerName,
                    address = pairing.baseUrl.substringAfter("://"),
                    desktopKey = AgentCrypto.shortFingerprint(pairing.desktopFingerprint),
                    access = if (canSendInput) R.string.devices_access_input else R.string.devices_access_read_only,
                    pairedOn = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(pairing.pairedAtMs)),
                    phoneName = pairing.deviceName,
                    phoneKey = phoneKey,
                )
            }.collect { state -> _devicesSecurityUiState.update { state.copy(showUnpairConfirm = it.showUnpairConfirm) } }
        }
    }

    fun onEvent(event: DevicesSecurityEvent) {
        when (event) {
            DevicesSecurityEvent.OnUnpairClick -> _devicesSecurityUiState.update { it.copy(showUnpairConfirm = true) }
            DevicesSecurityEvent.OnUnpairDismiss -> _devicesSecurityUiState.update { it.copy(showUnpairConfirm = false) }
            // The root reacts to the pairing disappearing and returns to onboarding.
            DevicesSecurityEvent.OnUnpairConfirm -> viewModelScope.launch { pairingRepository.unpair() }
        }
    }
}
