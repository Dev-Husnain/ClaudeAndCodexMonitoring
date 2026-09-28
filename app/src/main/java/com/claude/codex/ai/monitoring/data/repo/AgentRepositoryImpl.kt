package com.claude.codex.ai.monitoring.data.repo

import com.claude.codex.ai.monitoring.core.utils.Clock
import com.claude.codex.ai.monitoring.data.network.AgentSocketDataSource
import com.claude.codex.ai.monitoring.data.network.DesktopIdentityException
import com.claude.codex.ai.monitoring.data.network.ReconnectBackoff
import com.claude.codex.ai.monitoring.data.security.DeviceKeyDataSource
import com.claude.codex.ai.monitoring.domain.models.AgentSnapshotModel
import com.claude.codex.ai.monitoring.domain.models.AuthProblem
import com.claude.codex.ai.monitoring.domain.models.ConnectionStatus
import com.claude.codex.ai.monitoring.domain.models.PairingModel
import com.claude.codex.ai.monitoring.domain.repo.AgentRepository
import com.claude.codex.ai.monitoring.domain.repo.PairingRepository
import com.claude.codex.ai.monitoring.protocol.AgentCrypto
import com.claude.codex.ai.monitoring.protocol.AuthPayloads
import com.claude.codex.ai.monitoring.protocol.Message
import com.claude.codex.ai.monitoring.protocol.ProtocolConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the single authenticated connection to the paired computer. It runs while something
 * collects [snapshot] (plus a short grace period for configuration changes) and reconnects with
 * exponential back-off and jitter. The last known state is kept across reconnects. When the
 * computer refuses this phone, or cannot prove its own identity, it stops retrying until the
 * pairing changes.
 */
class AgentRepositoryImpl(
    private val socket: AgentSocketDataSource,
    private val keys: DeviceKeyDataSource,
    pairingRepository: PairingRepository,
    appScope: CoroutineScope,
    private val backoff: ReconnectBackoff,
    private val clock: Clock,
    private val appVersion: String,
) : AgentRepository {

    private val state = MutableStateFlow(AgentSnapshotModel())
    private val outgoing = Channel<Message>(capacity = OUTGOING_CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val reconnectRequests = Channel<Unit>(Channel.CONFLATED)
    private var currentTarget: PairingModel? = null

    override val snapshot: StateFlow<AgentSnapshotModel> = channelFlow {
        launch {
            pairingRepository.pairing.distinctUntilChanged().collectLatest { pairing ->
                if (pairing?.deviceId != currentTarget?.deviceId || pairing?.baseUrl != currentTarget?.baseUrl) {
                    // A different computer, address or identity: never show the previous one's data.
                    state.value = AgentSnapshotModel()
                }
                currentTarget = pairing
                if (pairing == null) awaitCancellation()
                runConnection(pairing)
            }
        }
        state.collect { send(it) }
    }.stateIn(appScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), state.value)

    override fun reconnectNow() {
        reconnectRequests.trySend(Unit)
    }

    override fun requestHistory(sessionId: String) {
        outgoing.trySend(Message.SessionHistory(sessionId))
    }

    private suspend fun runConnection(pairing: PairingModel) {
        val url = pairing.webSocketUrl()
        val desktopKey = AgentCrypto.decodePublicKey(pairing.desktopPublicKey)
        var failures = 0
        state.update { current ->
            if (current.connection is ConnectionStatus.Connected) current.copy(connection = ConnectionStatus.Connecting) else current
        }
        while (true) {
            // Requests queued while disconnected are stale; screens re-request once connected.
            while (outgoing.tryReceive().isSuccess) Unit
            var refusal: AuthProblem? = null
            try {
                socket.connect(
                    url = url,
                    hello = Message.Hello(pairing.deviceId, appVersion),
                    authenticate = { challenge ->
                        val genuine = AgentCrypto.verify(
                            desktopKey,
                            AuthPayloads.challenge(challenge.nonce, pairing.deviceId),
                            challenge.desktopSignature,
                        )
                        if (!genuine) throw DesktopIdentityException()
                        Message.Auth(keys.sign(AuthPayloads.auth(challenge.nonce, pairing.deviceId, pairing.desktopFingerprint)))
                    },
                    outgoing = outgoing,
                ).collect { message ->
                    refusal = AgentStateReducer.refusalOf(message) ?: refusal
                    state.update { AgentStateReducer.onMessage(it, message, clock.nowMs()) }
                    if (message is Message.Ready) failures = 0
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: DesktopIdentityException) {
                refusal = AuthProblem.DESKTOP_MISMATCH
            } catch (_: Exception) {
                // Network failure, refused connection or heartbeat timeout: handled by back-off below.
            }
            refusal?.let { problem ->
                state.update { AgentStateReducer.onUnauthorized(it, problem) }
                awaitCancellation() // Until the pairing changes.
            }
            failures++
            state.update { AgentStateReducer.onDisconnected(it, failures) }
            // Wait out the back-off, unless the user asks to retry now.
            withTimeoutOrNull(backoff.delayFor(failures)) { reconnectRequests.receive() }
        }
    }

    private fun PairingModel.webSocketUrl(): String = when {
        baseUrl.startsWith("https://") -> "wss://" + baseUrl.removePrefix("https://")
        else -> "ws://" + baseUrl.removePrefix("http://")
    } + ProtocolConstants.PATH_WS

    private companion object {
        const val OUTGOING_CAPACITY = 64
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
