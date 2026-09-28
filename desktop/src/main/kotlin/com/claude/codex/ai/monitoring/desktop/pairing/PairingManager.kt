package com.claude.codex.ai.monitoring.desktop.pairing

import com.claude.codex.ai.monitoring.desktop.devices.AuditCategory
import com.claude.codex.ai.monitoring.desktop.devices.AuditLog
import com.claude.codex.ai.monitoring.desktop.devices.DeviceGrant
import com.claude.codex.ai.monitoring.desktop.devices.DeviceStore
import com.claude.codex.ai.monitoring.desktop.devices.PairedDevice
import com.claude.codex.ai.monitoring.desktop.security.DesktopIdentity
import com.claude.codex.ai.monitoring.protocol.AgentCrypto
import com.claude.codex.ai.monitoring.protocol.AuthPayloads
import com.claude.codex.ai.monitoring.protocol.PairRequestDto
import com.claude.codex.ai.monitoring.protocol.PairResponseDto
import com.claude.codex.ai.monitoring.protocol.PairStatus
import com.claude.codex.ai.monitoring.protocol.PairingCode
import com.claude.codex.ai.monitoring.protocol.PairingOfferDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

data class PairingOffer(
    val code: String,
    val baseUrl: String,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    internal val token: String,
)

/** A phone waiting for the owner to approve it on the desktop. */
data class PendingApproval(
    val requestId: String,
    val deviceName: String,
    val deviceId: String,
    val fingerprint: String,
    val remote: String,
)

sealed interface PairingDecision {
    data class Approve(val grant: DeviceGrant) : PairingDecision
    data object Reject : PairingDecision
}

/**
 * One-time pairing (spec 6.2): a 128-bit token valid for two minutes and usable once. The phone's
 * request is held until the owner approves or rejects it on the desktop. Nothing is stored before
 * approval. [approvalTimeoutMs] stays under Cloudflare's 100-second origin timeout.
 */
class PairingManager(
    private val identity: DesktopIdentity,
    private val devices: DeviceStore,
    private val audit: AuditLog,
    private val computerName: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val tokenTtlMs: Long = 120_000L,
    private val approvalTimeoutMs: Long = 90_000L,
) {
    private val _offer = MutableStateFlow<PairingOffer?>(null)
    val offer: StateFlow<PairingOffer?> = _offer.asStateFlow()

    private val _pending = MutableStateFlow<PendingApproval?>(null)
    val pending: StateFlow<PendingApproval?> = _pending.asStateFlow()

    private var decision: CompletableDeferred<PairingDecision>? = null

    /** Creates a fresh offer; any previous unused token stops working. */
    fun createOffer(baseUrl: String): PairingOffer {
        val token = AgentCrypto.randomToken()
        val now = clock()
        val code = PairingCode.encode(PairingOfferDto(url = baseUrl, token = token, fp = identity.fingerprint, name = computerName))
        return PairingOffer(code, baseUrl, now, now + tokenTtlMs, token).also { _offer.value = it }
    }

    fun cancelOffer() {
        _offer.value = null
    }

    suspend fun handle(request: PairRequestDto, remote: String): PairResponseDto {
        val tokenStatus = consumeToken(request.token)
        if (tokenStatus != null) {
            audit.record(AuditCategory.PAIRING, "Pairing refused: ${tokenStatus.name.lowercase()} token", remote = remote)
            return PairResponseDto(tokenStatus)
        }
        val publicKey = runCatching { AgentCrypto.decodePublicKey(request.devicePublicKey) }.getOrNull()
        val validProof = publicKey != null &&
            AgentCrypto.verify(publicKey, AuthPayloads.pair(request.token, request.devicePublicKey), request.proof)
        if (!validProof) {
            audit.record(AuditCategory.PAIRING, "Pairing refused: key proof did not verify", remote = remote)
            return PairResponseDto(PairStatus.INVALID)
        }

        val deviceId = AgentCrypto.deviceIdFor(request.devicePublicKey)
        val fingerprint = AgentCrypto.fingerprint(request.devicePublicKey)
        val name = request.deviceName.trim().take(MAX_NAME).ifBlank { "Phone" }
        val deferred = CompletableDeferred<PairingDecision>()
        synchronized(this) {
            decision = deferred
            _pending.value = PendingApproval(UUID.randomUUID().toString(), name, deviceId, fingerprint, remote)
        }
        audit.record(AuditCategory.PAIRING, "Pairing requested by \"$name\"", deviceId, remote)

        val outcome = try {
            withTimeoutOrNull(approvalTimeoutMs) { deferred.await() }
        } finally {
            synchronized(this) {
                decision = null
                _pending.value = null
            }
        }

        return when (outcome) {
            null -> {
                audit.record(AuditCategory.PAIRING, "Pairing of \"$name\" timed out", deviceId, remote)
                PairResponseDto(PairStatus.TIMEOUT)
            }
            PairingDecision.Reject -> {
                audit.record(AuditCategory.PAIRING, "Pairing of \"$name\" rejected", deviceId, remote)
                PairResponseDto(PairStatus.REJECTED)
            }
            is PairingDecision.Approve -> {
                devices.save(
                    PairedDevice(
                        deviceId = deviceId,
                        name = name,
                        publicKey = request.devicePublicKey,
                        fingerprint = fingerprint,
                        grant = outcome.grant,
                        pairedAtMs = clock(),
                        lastSeenAtMs = null,
                    ),
                )
                audit.record(AuditCategory.PAIRING, "Paired \"$name\" (${outcome.grant.describe()})", deviceId, remote)
                PairResponseDto(
                    status = PairStatus.APPROVED,
                    deviceId = deviceId,
                    desktopPublicKey = identity.publicKeyBase64,
                    computerName = computerName,
                    canSendInput = outcome.grant.canSendInput,
                )
            }
        }
    }

    /** Called from the approval dialog. */
    fun decide(value: PairingDecision) {
        synchronized(this) { decision?.complete(value) }
    }

    /** Returns null when the token is valid (and consumes it), otherwise why it was refused. */
    private fun consumeToken(token: String): PairStatus? = synchronized(this) {
        val current = _offer.value
        when {
            current == null || !AgentCrypto.secretsEqual(current.token, token) -> PairStatus.INVALID
            clock() > current.expiresAtMs -> {
                _offer.value = null
                PairStatus.EXPIRED
            }
            else -> {
                _offer.value = null // single use
                null
            }
        }
    }

    private fun DeviceGrant.describe(): String {
        val scope = if (allProjects) "all projects" else "${projectIds.size} project(s)"
        return if (canSendInput) "$scope, can send input" else "$scope, read-only"
    }

    private companion object {
        const val MAX_NAME = 40
    }
}
