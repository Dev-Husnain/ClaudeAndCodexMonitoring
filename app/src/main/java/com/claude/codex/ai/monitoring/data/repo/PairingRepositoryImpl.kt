package com.claude.codex.ai.monitoring.data.repo

import android.os.Build
import com.claude.codex.ai.monitoring.core.utils.AppDispatchers
import com.claude.codex.ai.monitoring.core.utils.Clock
import com.claude.codex.ai.monitoring.data.local.PairingDataSource
import com.claude.codex.ai.monitoring.data.network.PairingApi
import com.claude.codex.ai.monitoring.data.security.DeviceKeyDataSource
import com.claude.codex.ai.monitoring.domain.models.PairingError
import com.claude.codex.ai.monitoring.domain.models.PairingModel
import com.claude.codex.ai.monitoring.domain.models.PairingOfferModel
import com.claude.codex.ai.monitoring.domain.repo.PairingRepository
import com.claude.codex.ai.monitoring.protocol.AgentCrypto
import com.claude.codex.ai.monitoring.protocol.AuthPayloads
import com.claude.codex.ai.monitoring.protocol.PairRequestDto
import com.claude.codex.ai.monitoring.protocol.PairStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

class PairingRepositoryImpl(
    private val keys: DeviceKeyDataSource,
    private val store: PairingDataSource,
    private val api: PairingApi,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    private val systemDeviceName: () -> String?,
) : PairingRepository {

    override val pairing: Flow<PairingModel?> = store.pairing.distinctUntilChanged()

    override suspend fun pair(offer: PairingOfferModel, deviceName: String): Result<PairingModel> =
        withContext(dispatchers.io) {
            val publicKey = keys.publicKeyBase64()
            val request = PairRequestDto(
                token = offer.token,
                devicePublicKey = publicKey,
                deviceName = deviceName,
                proof = keys.sign(AuthPayloads.pair(offer.token, publicKey)),
            )
            val response = try {
                api.pair(offer.baseUrl, request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withContext Result.failure(PairingError.Network(e))
            }
            when (response.status) {
                PairStatus.APPROVED -> {
                    val desktopKey = response.desktopPublicKey
                    val deviceId = response.deviceId
                    // Pin the desktop key only if it is exactly the one whose fingerprint was in the QR.
                    if (desktopKey == null || AgentCrypto.fingerprint(desktopKey) != offer.desktopFingerprint ||
                        deviceId != AgentCrypto.deviceIdFor(publicKey)
                    ) {
                        return@withContext Result.failure(PairingError.DesktopMismatch())
                    }
                    val model = PairingModel(
                        baseUrl = offer.baseUrl,
                        deviceId = deviceId,
                        deviceName = deviceName,
                        desktopPublicKey = desktopKey,
                        desktopFingerprint = offer.desktopFingerprint,
                        computerName = response.computerName ?: offer.computerName,
                        canSendInput = response.canSendInput,
                        pairedAtMs = clock.nowMs(),
                    )
                    store.save(model)
                    Result.success(model)
                }
                PairStatus.REJECTED -> Result.failure(PairingError.Rejected())
                PairStatus.EXPIRED, PairStatus.INVALID -> Result.failure(PairingError.Expired())
                PairStatus.TIMEOUT -> Result.failure(PairingError.TimedOut())
                PairStatus.RATE_LIMITED -> Result.failure(PairingError.RateLimited())
            }
        }

    override suspend fun unpair() = withContext(dispatchers.io) {
        store.clear()
        keys.delete()
    }

    override suspend fun deviceFingerprint(): String = withContext(dispatchers.io) {
        AgentCrypto.fingerprint(keys.publicKeyBase64())
    }

    override fun defaultDeviceName(): String =
        systemDeviceName()?.takeIf { it.isNotBlank() }
            ?: "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim()
}
