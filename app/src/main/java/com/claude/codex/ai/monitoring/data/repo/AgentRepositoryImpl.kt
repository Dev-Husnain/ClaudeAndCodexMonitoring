package com.claude.codex.ai.monitoring.data.repo

import com.claude.codex.ai.monitoring.core.utils.Clock
import com.claude.codex.ai.monitoring.data.network.AgentSocketDataSource
import com.claude.codex.ai.monitoring.data.network.ReconnectBackoff
import com.claude.codex.ai.monitoring.domain.models.AgentSnapshotModel
import com.claude.codex.ai.monitoring.domain.models.ConnectionStatus
import com.claude.codex.ai.monitoring.domain.repo.AgentRepository
import com.claude.codex.ai.monitoring.domain.repo.SettingsRepository
import com.claude.codex.ai.monitoring.protocol.Message
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the single connection to the desktop agent. The connection runs while something collects
 * [snapshot] (plus a short grace period for configuration changes) and reconnects with
 * exponential back-off and jitter. The last known state is kept across reconnects.
 */
class AgentRepositoryImpl(
    private val socket: AgentSocketDataSource,
    settingsRepository: SettingsRepository,
    appScope: CoroutineScope,
    private val backoff: ReconnectBackoff,
    private val clock: Clock,
    private val appVersion: String,
) : AgentRepository {

    private data class Target(val url: String, val deviceId: String)

    private val state = MutableStateFlow(AgentSnapshotModel())
    private val outgoing = Channel<Message>(capacity = OUTGOING_CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val reconnectRequests = Channel<Unit>(Channel.CONFLATED)
    private var currentTarget: Target? = null

    private val targets: Flow<Target> = settingsRepository.settings
        .map { Target(it.serverUrl, it.deviceId) }
        .distinctUntilChanged()

    override val snapshot: StateFlow<AgentSnapshotModel> = channelFlow {
        launch {
            targets.collectLatest { target ->
                if (target != currentTarget) {
                    // A different computer or address: never show the previous one's sessions.
                    if (currentTarget != null) state.value = AgentSnapshotModel()
                    currentTarget = target
                }
                runConnection(target)
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

    private suspend fun runConnection(target: Target) {
        var failures = 0
        state.update { current ->
            if (current.connection is ConnectionStatus.Connected) current.copy(connection = ConnectionStatus.Connecting) else current
        }
        while (true) {
            // Requests queued while disconnected are stale; screens re-request once connected.
            while (outgoing.tryReceive().isSuccess) Unit
            try {
                socket.connect(target.url, Message.Hello(target.deviceId, appVersion), outgoing).collect { message ->
                    state.update { AgentStateReducer.onMessage(it, message, clock.nowMs()) }
                    if (message is Message.Ready) failures = 0
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Network failure, refused connection or heartbeat timeout: handled by back-off below.
            }
            failures++
            state.update { AgentStateReducer.onDisconnected(it, failures) }
            // Wait out the back-off, unless the user asks to retry now.
            withTimeoutOrNull(backoff.delayFor(failures)) { reconnectRequests.receive() }
        }
    }

    private companion object {
        const val OUTGOING_CAPACITY = 64
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
