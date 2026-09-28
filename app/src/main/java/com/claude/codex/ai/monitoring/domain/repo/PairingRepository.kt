package com.claude.codex.ai.monitoring.domain.repo

import com.claude.codex.ai.monitoring.domain.models.PairingModel
import com.claude.codex.ai.monitoring.domain.models.PairingOfferModel
import kotlinx.coroutines.flow.Flow

interface PairingRepository {
    /** The current pairing, or null when this phone is not paired. */
    val pairing: Flow<PairingModel?>

    /** Sends the pairing request and waits for the owner to approve it on the computer. */
    suspend fun pair(offer: PairingOfferModel, deviceName: String): Result<PairingModel>

    /** Forgets the computer and deletes this phone's key; the next pairing uses a fresh key. */
    suspend fun unpair()

    /** Fingerprint of this phone's public key, shown so the owner can compare it on the computer. */
    suspend fun deviceFingerprint(): String

    fun defaultDeviceName(): String
}
