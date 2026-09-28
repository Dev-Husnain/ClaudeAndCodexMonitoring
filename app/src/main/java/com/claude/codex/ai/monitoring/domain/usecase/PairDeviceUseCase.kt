package com.claude.codex.ai.monitoring.domain.usecase

import com.claude.codex.ai.monitoring.domain.models.PairingError
import com.claude.codex.ai.monitoring.domain.models.PairingModel
import com.claude.codex.ai.monitoring.domain.models.PairingOfferModel
import com.claude.codex.ai.monitoring.domain.repo.PairingRepository

/** Validates the device name the owner will see, then runs the pairing request. */
class PairDeviceUseCase(
    private val pairingRepository: PairingRepository,
) {
    suspend operator fun invoke(offer: PairingOfferModel, deviceName: String): Result<PairingModel> {
        val name = deviceName.trim()
        if (name.isEmpty() || name.length > MAX_NAME) return Result.failure(PairingError.InvalidName())
        return pairingRepository.pair(offer, name)
    }

    companion object {
        const val MAX_NAME = 40
    }
}
