package com.claude.codex.ai.monitoring.desktop.server

import com.claude.codex.ai.monitoring.desktop.session.SessionRegistry
import com.claude.codex.ai.monitoring.protocol.ComputerDto
import com.claude.codex.ai.monitoring.protocol.ControlMode
import com.claude.codex.ai.monitoring.protocol.ErrorCode
import com.claude.codex.ai.monitoring.protocol.EventKind
import com.claude.codex.ai.monitoring.protocol.Message
import com.claude.codex.ai.monitoring.protocol.ProjectDto
import com.claude.codex.ai.monitoring.protocol.ProtocolCodec
import com.claude.codex.ai.monitoring.protocol.SessionDto
import com.claude.codex.ai.monitoring.protocol.SessionState
import com.claude.codex.ai.monitoring.protocol.TimelineEventDto
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AgentServerTest {

    private val codec = ProtocolCodec()
    private val registry = SessionRegistry(ComputerDto("c1", "Laptop")).apply {
        upsertProject(ProjectDto("p1", "App"))
        upsertSession(SessionDto("s1", "p1", SessionState.RUNNING, ControlMode.MONITOR_ONLY, 1, 2))
        addEvent(TimelineEventDto("e1", "s1", 3, EventKind.PROMPT, "Prompt"))
    }
    private val connections = ConnectionTracker()

    private fun ApplicationTestBuilder.setUp() {
        application { agentModule(ClientHandler(registry, codec, connections, helloTimeoutMs = 2_000), "test") }
    }

    private suspend fun DefaultClientWebSocketSession.sendMessage(message: Message) =
        send(Frame.Text(codec.encode(message)))

    private suspend fun DefaultClientWebSocketSession.receiveMessage(): Message = withTimeout(5_000) {
        codec.decode((incoming.receive() as Frame.Text).readText()).getOrThrow().message
    }

    @Test
    fun `health endpoint reports ok`() = testApplication {
        setUp()
        assertTrue(client.get("/health").bodyAsText().contains("\"ok\""))
    }

    @Test
    fun `hello receives the ready snapshot then ping pong and history work`() = testApplication {
        setUp()
        val ws = createClient { install(WebSockets) }
        ws.webSocket("/ws") {
            sendMessage(Message.Hello("device-1", "1.0"))
            val ready = assertIs<Message.Ready>(receiveMessage())
            assertEquals(listOf("s1"), ready.sessions.map { it.sessionId })
            assertEquals(1, connections.connected.value["device-1"])

            sendMessage(Message.Ping)
            assertEquals(Message.Pong, receiveMessage())

            sendMessage(Message.SessionHistory("s1"))
            val history = assertIs<Message.SessionHistoryResult>(receiveMessage())
            assertEquals(listOf("e1"), history.events.map { it.eventId })
        }
    }

    @Test
    fun `live registry changes are pushed to the phone`() = testApplication {
        setUp()
        val ws = createClient { install(WebSockets) }
        ws.webSocket("/ws") {
            sendMessage(Message.Hello("device-1", "1.0"))
            assertIs<Message.Ready>(receiveMessage())
            // Round-trip a ping so the server has definitely subscribed to updates.
            sendMessage(Message.Ping)
            assertEquals(Message.Pong, receiveMessage())

            registry.upsertSession(SessionDto("s1", "p1", SessionState.WAITING_INPUT, ControlMode.MONITOR_ONLY, 1, 9))
            val update = assertIs<Message.SessionUpdate>(receiveMessage())
            assertEquals(SessionState.WAITING_INPUT, update.session.state)
        }
    }

    @Test
    fun `a first message other than hello is rejected`() = testApplication {
        setUp()
        val ws = createClient { install(WebSockets) }
        ws.webSocket("/ws") {
            sendMessage(Message.Ping)
            val error = assertIs<Message.Error>(receiveMessage())
            assertEquals(ErrorCode.BAD_REQUEST, error.code)
        }
    }

    @Test
    fun `input is refused until control is implemented`() = testApplication {
        setUp()
        val ws = createClient { install(WebSockets) }
        ws.webSocket("/ws") {
            sendMessage(Message.Hello("device-1", "1.0"))
            assertIs<Message.Ready>(receiveMessage())
            sendMessage(Message.SendInput("s1", "continue"))
            assertEquals(ErrorCode.SESSION_NOT_CONTROLLABLE, assertIs<Message.Error>(receiveMessage()).code)
        }
    }
}
