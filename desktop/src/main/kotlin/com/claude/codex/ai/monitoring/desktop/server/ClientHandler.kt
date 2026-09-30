package com.claude.codex.ai.monitoring.desktop.server

import com.claude.codex.ai.monitoring.desktop.control.ControlCenter
import com.claude.codex.ai.monitoring.desktop.devices.AuditCategory
import com.claude.codex.ai.monitoring.desktop.devices.AuditLog
import com.claude.codex.ai.monitoring.desktop.devices.DeviceStore
import com.claude.codex.ai.monitoring.desktop.devices.PairedDevice
import com.claude.codex.ai.monitoring.desktop.security.DesktopIdentity
import com.claude.codex.ai.monitoring.desktop.security.RateLimiter
import com.claude.codex.ai.monitoring.desktop.session.SessionRegistry
import com.claude.codex.ai.monitoring.desktop.wrapper.WrapperHub
import com.claude.codex.ai.monitoring.protocol.AgentCrypto
import com.claude.codex.ai.monitoring.protocol.AuthPayloads
import com.claude.codex.ai.monitoring.protocol.DeliveryResult
import com.claude.codex.ai.monitoring.protocol.ErrorCode
import com.claude.codex.ai.monitoring.protocol.Frame as ProtocolFrame
import com.claude.codex.ai.monitoring.protocol.Message
import com.claude.codex.ai.monitoring.protocol.ProtocolCodec
import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Serves one phone connection (spec 6.3):
 * 1. `hello {deviceId}`: unknown devices get NOT_PAIRED.
 * 2. `challenge {nonce, desktopSignature}`: a fresh single-use nonce, signed by the desktop.
 * 3. `auth {signature}` within [authTimeoutMs]: verified against the stored device key.
 * 4. `ready` and live updates, filtered to the projects this device was granted (spec 6.4).
 * Failed attempts are rate-limited per IP and per device, and every outcome is audited.
 */
class ClientHandler(
    private val registry: SessionRegistry,
    private val codec: ProtocolCodec,
    private val hub: ConnectionHub,
    private val devices: DeviceStore,
    private val identity: DesktopIdentity,
    private val audit: AuditLog,
    private val rateLimiter: RateLimiter,
    private val control: ControlCenter? = null,
    private val wrappers: WrapperHub? = null,
    private val helloTimeoutMs: Long = HELLO_TIMEOUT_MS,
    private val authTimeoutMs: Long = AUTH_TIMEOUT_MS,
) {
    suspend fun handle(session: DefaultWebSocketSession, remote: String) = with(session) {
        val ipKey = "ip:$remote"
        if (rateLimiter.isLimited(ipKey)) return reject(ErrorCode.RATE_LIMITED, "Too many failed attempts")

        val hello = withTimeoutOrNull(helloTimeoutMs) { receiveFrame() }?.message as? Message.Hello
            ?: return reject(ErrorCode.BAD_REQUEST, "Expected hello")
        val deviceKey = "device:${hello.deviceId}"
        if (rateLimiter.isLimited(deviceKey)) return reject(ErrorCode.RATE_LIMITED, "Too many failed attempts")

        val device = devices.find(hello.deviceId)
        if (device == null) {
            rateLimiter.recordFailure(ipKey)
            audit.record(AuditCategory.AUTH, "Connection from an unpaired device", hello.deviceId, remote)
            return reject(ErrorCode.NOT_PAIRED, "This device is not paired")
        }

        val nonce = AgentCrypto.randomToken(NONCE_BYTES)
        send(Frame.Text(codec.encode(Message.Challenge(nonce, identity.sign(AuthPayloads.challenge(nonce, device.deviceId))))))
        val auth = withTimeoutOrNull(authTimeoutMs) { receiveFrame() }?.message as? Message.Auth
        val verified = auth != null && AgentCrypto.verify(
            AgentCrypto.decodePublicKey(device.publicKey),
            AuthPayloads.auth(nonce, device.deviceId, identity.fingerprint),
            auth.signature,
        )
        if (!verified) {
            rateLimiter.recordFailure(ipKey)
            rateLimiter.recordFailure(deviceKey)
            audit.record(AuditCategory.AUTH, "Authentication failed for \"${device.name}\"", device.deviceId, remote)
            return reject(ErrorCode.AUTH_FAILED, "Authentication failed")
        }

        rateLimiter.reset(deviceKey)
        devices.markSeen(device.deviceId)
        hub.register(device.deviceId, this)
        try {
            serve(device)
        } finally {
            hub.unregister(device.deviceId, this)
            devices.markSeen(device.deviceId)
        }
    }

    private suspend fun DefaultWebSocketSession.serve(device: PairedDevice) = coroutineScope {
        val grant = device.grant
        var subscription: Set<String>? = null // null = every granted project
        fun visible(projectId: String?) = projectId != null && grant.allows(projectId) &&
            (subscription?.contains(projectId) ?: true)

        send(Frame.Text(codec.encode(readyFor(device))))
        val forwarder = launch {
            registry.updates.collect { update ->
                val projectId = update.projectIdOrNull()
                // Away mode is global; everything else is scoped to a project this device may see.
                if (update is Message.AwayModeUpdate || visible(projectId)) send(Frame.Text(codec.encode(update, projectId = projectId)))
            }
        }
        // At most one terminal per connection: the screen the phone has open.
        var terminal: Job? = null
        while (true) {
            val frame = receiveFrame() ?: break
            val reply: Message? = when (val message = frame.message) {
                Message.Ping -> Message.Pong
                Message.SessionList -> readyFor(device)
                is Message.Subscribe -> {
                    val requested = message.projectIds.toSet()
                    val forbidden = requested.filterNot(grant::allows)
                    subscription = requested.filter(grant::allows).toSet().ifEmpty { null }
                    if (forbidden.isNotEmpty()) Message.Error(ErrorCode.FORBIDDEN_PROJECT, "Not allowed: ${forbidden.size} project(s)", frame.id) else null
                }
                is Message.SessionHistory -> {
                    val projectId = registry.projectOf(message.sessionId)
                    when {
                        projectId == null -> Message.Error(ErrorCode.SESSION_NOT_FOUND, "Unknown session", frame.id)
                        !grant.allows(projectId) -> Message.Error(ErrorCode.FORBIDDEN_PROJECT, "Not allowed", frame.id)
                        else -> registry.history(message.sessionId, message.beforeTs)
                    }
                }
                is Message.SendInput, is Message.QuickActionRequest, is Message.SetAwayMode, is Message.TerminalKeyRequest ->
                    if (!grant.canSendInput) {
                        Message.Error(ErrorCode.READ_ONLY, "This device is read-only", frame.id)
                    } else {
                        control(message, frame.id, device)
                    }
                is Message.TerminalAttach -> {
                    val projectId = registry.projectOf(message.sessionId)
                    terminal?.cancel()
                    terminal = null
                    when {
                        projectId == null -> Message.Error(ErrorCode.SESSION_NOT_FOUND, "Unknown session", frame.id)
                        !grant.allows(projectId) -> Message.Error(ErrorCode.FORBIDDEN_PROJECT, "Not allowed", frame.id)
                        wrappers == null -> Message.Error(ErrorCode.SESSION_NOT_CONTROLLABLE, "No terminal for this session", frame.id)
                        else -> {
                            // Watching is allowed for read-only devices too; typing is not.
                            terminal = launch { wrappers.screens(message.sessionId).collect { send(Frame.Text(codec.encode(it, projectId = projectId))) } }
                            null
                        }
                    }
                }
                Message.TerminalDetach -> {
                    terminal?.cancel()
                    terminal = null
                    null
                }
                else -> Message.Error(ErrorCode.BAD_REQUEST, "Unexpected message", frame.id)
            }
            reply?.let { send(Frame.Text(codec.encode(it))) }
        }
        terminal?.cancel()
        forwarder.cancel()
    }

    private fun control(message: Message, ackId: String, device: PairedDevice): Message {
        val center = control ?: return Message.Error(ErrorCode.SESSION_NOT_CONTROLLABLE, "Control is not available", ackId)
        if (message is Message.SetAwayMode) {
            center.setAwayMode(message.enabled, by = device.name)
            return Message.Ack(ackId, DeliveryResult.DELIVERED)
        }
        val sessionId = when (message) {
            is Message.SendInput -> message.sessionId
            is Message.QuickActionRequest -> message.sessionId
            is Message.TerminalKeyRequest -> message.sessionId
            else -> return Message.Error(ErrorCode.BAD_REQUEST, "Unexpected message", ackId)
        }
        val projectId = registry.projectOf(sessionId) ?: return Message.Error(ErrorCode.SESSION_NOT_FOUND, "Unknown session", ackId)
        if (!device.grant.allows(projectId)) return Message.Error(ErrorCode.FORBIDDEN_PROJECT, "Not allowed", ackId)
        val delivery = when (message) {
            is Message.SendInput -> center.deliverText(sessionId, message.text)
            is Message.QuickActionRequest -> center.quickAction(sessionId, message.action)
            is Message.TerminalKeyRequest -> center.pressKey(sessionId, message.key)
            else -> return Message.Error(ErrorCode.BAD_REQUEST, "Unexpected message", ackId)
        }
        return Message.Ack(ackId, delivery.result, delivery.detail)
    }

    private fun readyFor(device: PairedDevice): Message.Ready {
        val all = registry.snapshot()
        return all.copy(
            projects = all.projects.filter { device.grant.allows(it.projectId) },
            sessions = all.sessions.filter { device.grant.allows(it.projectId) },
            canSendInput = device.grant.canSendInput,
            awayMode = control?.awayMode?.value ?: false,
        )
    }

    private suspend fun DefaultWebSocketSession.reject(code: ErrorCode, message: String) {
        runCatching {
            send(Frame.Text(codec.encode(Message.Error(code, message))))
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, message))
        }
    }

    /** Next decoded frame, skipping undecodable ones; null when the socket closes. */
    private suspend fun DefaultWebSocketSession.receiveFrame(): ProtocolFrame? {
        while (true) {
            val frame = incoming.receiveCatching().getOrNull() ?: return null
            if (frame !is Frame.Text) continue
            codec.decode(frame.readText()).onSuccess { return it }
        }
    }

    private fun Message.projectIdOrNull(): String? = when (this) {
        is Message.SessionUpdate -> session.projectId
        is Message.SessionEvent -> registry.projectOf(sessionId)
        is Message.SessionRemoved -> projectId
        else -> null
    }

    private companion object {
        const val HELLO_TIMEOUT_MS = 10_000L
        const val AUTH_TIMEOUT_MS = 30_000L
        const val NONCE_BYTES = 32
    }
}
