package com.claude.codex.ai.monitoring.fakes

import com.claude.codex.ai.monitoring.domain.models.PairingModel
import com.claude.codex.ai.monitoring.domain.models.PairingOfferModel
import com.claude.codex.ai.monitoring.domain.repo.PairingRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

class FakePairingRepository(initial: PairingModel? = null) : PairingRepository {
    override val pairing = MutableStateFlow(initial)
    var unpairCalls = 0
    val requests = mutableListOf<Pair<PairingOfferModel, String>>()

    /** Completed by the test to decide what the "computer" answers. */
    var nextResult = CompletableDeferred<Result<PairingModel>>()

    override suspend fun pair(offer: PairingOfferModel, deviceName: String): Result<PairingModel> {
        requests += offer to deviceName
        return nextResult.await().onSuccess { pairing.value = it }
    }

    override suspend fun unpair() {
        unpairCalls++
        pairing.value = null
    }

    override suspend fun deviceFingerprint(): String = "f".repeat(64)

    override fun defaultDeviceName(): String = "Test Phone"

    companion object {
        fun pairing(computerName: String = "Laptop", canSendInput: Boolean = false) = PairingModel(
            baseUrl = "https://agent.example",
            deviceId = "device",
            deviceName = "Test Phone",
            desktopPublicKey = "key",
            desktopFingerprint = "a".repeat(64),
            computerName = computerName,
            canSendInput = canSendInput,
            pairedAtMs = 0L,
        )
    }
}
