package com.claude.codex.ai.monitoring.desktop

import com.claude.codex.ai.monitoring.desktop.devices.AuditLog
import com.claude.codex.ai.monitoring.desktop.devices.DeviceGrant
import com.claude.codex.ai.monitoring.desktop.devices.DeviceStore
import com.claude.codex.ai.monitoring.desktop.devices.PairedDevice
import com.claude.codex.ai.monitoring.desktop.pairing.PairingManager
import com.claude.codex.ai.monitoring.desktop.security.DesktopIdentity
import com.claude.codex.ai.monitoring.desktop.security.RateLimiter
import com.claude.codex.ai.monitoring.desktop.server.ClientHandler
import com.claude.codex.ai.monitoring.desktop.server.ConnectionHub
import com.claude.codex.ai.monitoring.desktop.session.SessionRegistry
import com.claude.codex.ai.monitoring.desktop.storage.AppStorage
import com.claude.codex.ai.monitoring.protocol.AgentCrypto
import com.claude.codex.ai.monitoring.protocol.AuthPayloads
import com.claude.codex.ai.monitoring.protocol.ComputerDto
import com.claude.codex.ai.monitoring.protocol.ControlMode
import com.claude.codex.ai.monitoring.protocol.Message
import com.claude.codex.ai.monitoring.protocol.ProjectDto
import com.claude.codex.ai.monitoring.protocol.ProtocolCodec
import com.claude.codex.ai.monitoring.protocol.SessionDto
import com.claude.codex.ai.monitoring.protocol.SessionState
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import java.security.KeyPair

/** A fully wired agent over an in-memory database, plus helpers that act like a phone. */
class TestAgent(clock: () -> Long = System::currentTimeMillis) {
    val codec = ProtocolCodec()
    val identity = DesktopIdentity(AgentCrypto.generateKeyPair())
    private val database = AppStorage.openDatabase(null)
    val devices = DeviceStore(database, clock)
    val audit = AuditLog(database, clock)
    val hub = ConnectionHub(codec)
    val rateLimiter = RateLimiter(maxFailures = 3, clock = clock)
    val registry = SessionRegistry(ComputerDto("c1", "Laptop")).apply {
        upsertProject(ProjectDto("p1", "Alpha"))
        upsertProject(ProjectDto("p2", "Beta"))
        upsertSession(SessionDto("s1", "p1", SessionState.RUNNING, ControlMode.WRAPPER, 1, 2))
        upsertSession(SessionDto("s2", "p2", SessionState.RUNNING, ControlMode.WRAPPER, 1, 2))
    }
    val pairing = PairingManager(identity, devices, audit, "Laptop", clock, approvalTimeoutMs = 2_000)
    val handler = ClientHandler(registry, codec, hub, devices, identity, audit, rateLimiter, helloTimeoutMs = 2_000, authTimeoutMs = 2_000)

    /** Registers a phone directly (as if pairing had completed) and returns its keys and id. */
    fun pairDevice(grant: DeviceGrant): Phone {
        val keys = AgentCrypto.generateKeyPair()
        val publicKey = AgentCrypto.encodePublicKey(keys.public)
        val deviceId = AgentCrypto.deviceIdFor(publicKey)
        devices.save(PairedDevice(deviceId, "Phone", publicKey, AgentCrypto.fingerprint(publicKey), grant, 0, null))
        return Phone(keys, deviceId)
    }

    data class Phone(val keys: KeyPair, val deviceId: String)

    suspend fun DefaultClientWebSocketSession.sendMessage(message: Message) = send(Frame.Text(codec.encode(message)))

    suspend fun DefaultClientWebSocketSession.receiveMessage(): Message = withTimeout(5_000) {
        codec.decode((incoming.receive() as Frame.Text).readText()).getOrThrow().message
    }

    /** Runs hello/challenge/auth like the app does and returns the message that follows auth. */
    suspend fun DefaultClientWebSocketSession.authenticate(phone: Phone, sign: (String) -> String = { nonce ->
        AgentCrypto.sign(phone.keys.private, AuthPayloads.auth(nonce, phone.deviceId, identity.fingerprint))
    }): Message {
        sendMessage(Message.Hello(phone.deviceId, "test"))
        val challenge = receiveMessage() as? Message.Challenge ?: error("expected challenge")
        check(AgentCrypto.verify(AgentCrypto.decodePublicKey(identity.publicKeyBase64), AuthPayloads.challenge(challenge.nonce, phone.deviceId), challenge.desktopSignature))
        sendMessage(Message.Auth(sign(challenge.nonce)))
        return receiveMessage()
    }

    companion object {
        val ReadOnlyAll = DeviceGrant(canSendInput = false, allProjects = true, projectIds = emptySet())
    }
}
