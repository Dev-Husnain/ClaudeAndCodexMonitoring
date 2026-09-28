package com.claude.codex.ai.monitoring.desktop.server

import com.claude.codex.ai.monitoring.desktop.TestAgent
import com.claude.codex.ai.monitoring.desktop.devices.DeviceGrant
import com.claude.codex.ai.monitoring.desktop.security.RateLimiter
import com.claude.codex.ai.monitoring.protocol.AgentCrypto
import com.claude.codex.ai.monitoring.protocol.AuthPayloads
import com.claude.codex.ai.monitoring.protocol.ControlMode
import com.claude.codex.ai.monitoring.protocol.ErrorCode
import com.claude.codex.ai.monitoring.protocol.Message
import com.claude.codex.ai.monitoring.protocol.SessionDto
import com.claude.codex.ai.monitoring.protocol.SessionState
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AgentServerTest {

    private val agent = TestAgent()

    private fun ApplicationTestBuilder.setUp() {
        application { agentModule(agent.handler, agent.pairing, RateLimiter(), "test") }
    }

    private fun ApplicationTestBuilder.ws() = createClient { install(WebSockets) }

    @Test
    fun `health endpoint reports ok`() = testApplication {
        setUp()
        assertTrue(client.get("/health").bodyAsText().contains("\"ok\""))
    }

    @Test
    fun `paired device authenticates and gets ping pong and history`() = testApplication {
        setUp()
        val phone = agent.pairDevice(TestAgent.ReadOnlyAll)
        ws().webSocket("/ws") {
            with(agent) {
                val ready = assertIs<Message.Ready>(authenticate(phone))
                assertEquals(setOf("s1", "s2"), ready.sessions.map { it.sessionId }.toSet())
                assertEquals(false, ready.canSendInput)
                sendMessage(Message.Ping)
                assertEquals(Message.Pong, receiveMessage())
                sendMessage(Message.SessionHistory("s1"))
                assertIs<Message.SessionHistoryResult>(receiveMessage())
            }
        }
    }

    @Test
    fun `unpaired device is rejected before any data`() = testApplication {
        setUp()
        ws().webSocket("/ws") {
            with(agent) {
                sendMessage(Message.Hello("unknown-device", "test"))
                assertEquals(ErrorCode.NOT_PAIRED, assertIs<Message.Error>(receiveMessage()).code)
            }
        }
    }

    @Test
    fun `signature from another key is rejected`() = testApplication {
        setUp()
        val phone = agent.pairDevice(TestAgent.ReadOnlyAll)
        val impostor = AgentCrypto.generateKeyPair()
        ws().webSocket("/ws") {
            with(agent) {
                val reply = authenticate(phone) { nonce ->
                    AgentCrypto.sign(impostor.private, AuthPayloads.auth(nonce, phone.deviceId, identity.fingerprint))
                }
                assertEquals(ErrorCode.AUTH_FAILED, assertIs<Message.Error>(reply).code)
            }
        }
    }

    @Test
    fun `replayed auth signature from an earlier nonce is rejected`() = testApplication {
        setUp()
        val phone = agent.pairDevice(TestAgent.ReadOnlyAll)
        var captured: String? = null
        ws().webSocket("/ws") {
            with(agent) {
                authenticate(phone) { nonce ->
                    AgentCrypto.sign(phone.keys.private, AuthPayloads.auth(nonce, phone.deviceId, identity.fingerprint)).also { captured = it }
                }
            }
        }
        ws().webSocket("/ws") {
            with(agent) {
                val reply = authenticate(phone) { _ -> captured!! }
                assertEquals(ErrorCode.AUTH_FAILED, assertIs<Message.Error>(reply).code)
            }
        }
    }

    @Test
    fun `device only sees and receives its granted projects`() = testApplication {
        setUp()
        val phoneA = agent.pairDevice(DeviceGrant(canSendInput = false, allProjects = false, projectIds = setOf("p1")))
        ws().webSocket("/ws") {
            with(agent) {
                val ready = assertIs<Message.Ready>(authenticate(phoneA))
                assertEquals(listOf("p1"), ready.projects.map { it.projectId })
                assertEquals(listOf("s1"), ready.sessions.map { it.sessionId })

                sendMessage(Message.SessionHistory("s2"))
                assertEquals(ErrorCode.FORBIDDEN_PROJECT, assertIs<Message.Error>(receiveMessage()).code)

                // A project B update must never arrive; the following project A update must.
                registry.upsertSession(SessionDto("s2", "p2", SessionState.WAITING_INPUT, ControlMode.WRAPPER, 1, 3))
                registry.upsertSession(SessionDto("s1", "p1", SessionState.IDLE, ControlMode.WRAPPER, 1, 4))
                val update = assertIs<Message.SessionUpdate>(receiveMessage())
                assertEquals("s1", update.session.sessionId)
            }
        }
    }

    @Test
    fun `read-only device cannot send input`() = testApplication {
        setUp()
        val phone = agent.pairDevice(TestAgent.ReadOnlyAll)
        ws().webSocket("/ws") {
            with(agent) {
                assertIs<Message.Ready>(authenticate(phone))
                sendMessage(Message.SendInput("s1", "rm -rf"))
                assertEquals(ErrorCode.READ_ONLY, assertIs<Message.Error>(receiveMessage()).code)
            }
        }
    }

    @Test
    fun `revoked device is told and disconnected immediately`() = testApplication {
        setUp()
        val phone = agent.pairDevice(TestAgent.ReadOnlyAll)
        ws().webSocket("/ws") {
            with(agent) {
                assertIs<Message.Ready>(authenticate(phone))
                sendMessage(Message.Ping)
                assertEquals(Message.Pong, receiveMessage())
                devices.remove(phone.deviceId)
                hub.revoke(phone.deviceId)
                assertEquals(Message.Revoked, receiveMessage())
            }
        }
        ws().webSocket("/ws") {
            with(agent) {
                sendMessage(Message.Hello(phone.deviceId, "test"))
                assertEquals(ErrorCode.NOT_PAIRED, assertIs<Message.Error>(receiveMessage()).code)
            }
        }
    }

    @Test
    fun `repeated failures are rate limited`() = testApplication {
        setUp()
        repeat(3) {
            ws().webSocket("/ws") {
                with(agent) {
                    sendMessage(Message.Hello("nobody-$it", "test"))
                    receiveMessage()
                }
            }
        }
        ws().webSocket("/ws") {
            with(agent) {
                assertEquals(ErrorCode.RATE_LIMITED, assertIs<Message.Error>(receiveMessage()).code)
            }
        }
    }
}
