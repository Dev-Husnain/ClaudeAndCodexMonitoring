package com.claude.codex.ai.monitoring.desktop.wrapper

import com.claude.codex.ai.monitoring.desktop.TestAgent
import com.claude.codex.ai.monitoring.desktop.control.ControlCenter
import com.claude.codex.ai.monitoring.desktop.devices.DeviceGrant
import com.claude.codex.ai.monitoring.desktop.hooks.HookReceiver
import com.claude.codex.ai.monitoring.desktop.projects.ProjectStore
import com.claude.codex.ai.monitoring.desktop.security.RateLimiter
import com.claude.codex.ai.monitoring.desktop.server.ClientHandler
import com.claude.codex.ai.monitoring.desktop.server.agentModule
import com.claude.codex.ai.monitoring.desktop.session.SessionTracker
import com.claude.codex.ai.monitoring.desktop.storage.AppStorage
import com.claude.codex.ai.monitoring.protocol.ControlMode
import com.claude.codex.ai.monitoring.protocol.DeliveryResult
import com.claude.codex.ai.monitoring.protocol.ErrorCode
import com.claude.codex.ai.monitoring.protocol.Message
import com.claude.codex.ai.monitoring.protocol.ProtocolConstants
import com.claude.codex.ai.monitoring.protocol.QuickAction
import com.claude.codex.ai.monitoring.protocol.SessionState
import com.claude.codex.ai.monitoring.protocol.TerminalKey
import com.claude.codex.ai.monitoring.protocol.WrapperMessage
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `agentmon claude` ↔ agent ↔ phone, over the real routes. */
class WrapperLinkTest {

    private val agent = TestAgent()
    private val projectDir = Files.createTempDirectory("agentmon-wrapper")
    private val projects = ProjectStore(AppStorage.openDatabase(null)).apply { add(projectDir) }
    private val tracker = SessionTracker(agent.registry, projects)
    private val control = ControlCenter(agent.registry, agent.audit)
    private val secret = "w".repeat(43)
    private val hub = WrapperHub(
        agent.registry,
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
        projectFor = { projects.projectFor(it)?.projectId },
        submitDelayMs = 10,
    )
    private val receiver = HookReceiver(secret, tracker, agent.audit, control)
    private val handler = ClientHandler(agent.registry, agent.codec, agent.hub, agent.devices, agent.identity, agent.audit, RateLimiter(), control, hub)
    private val projectId = projects.projects.value.single().projectId

    init {
        control.wrapper = hub
    }

    private fun ApplicationTestBuilder.setUp() {
        application { agentModule(handler, agent.pairing, RateLimiter(), "test", receiver, WrapperEndpoint(secret, hub, agent.audit)) }
    }

    private fun ApplicationTestBuilder.ws() = createClient { install(WebSockets) }

    private suspend fun hook(event: String, wrapperId: String) = receiver.receive(
        secret,
        viaTunnel = false,
        body = """{"session_id":"real-1","hook_event_name":"$event","cwd":${quoted(projectDir.toString())},"prompt":"hi"}""",
        wrapperHeader = wrapperId,
    )

    private fun quoted(s: String) = "\"" + s.replace("\\", "\\\\") + "\""

    private suspend fun DefaultClientWebSocketSession.hello(id: String) =
        send(Frame.Text(WrapperMessage.encode(WrapperMessage.Hello(id, projectDir.resolve("src").toString(), 80, 24))))

    private suspend fun DefaultClientWebSocketSession.nextInput(): String = withTimeout(5_000) {
        assertIs<WrapperMessage.Input>(WrapperMessage.decode((incoming.receive() as Frame.Text).readText())).data
    }

    private suspend fun awaitWrapped(sessionId: String, wrapped: Boolean = true) {
        repeat(300) {
            if (hub.isWrapped(sessionId) == wrapped) return
            delay(10)
        }
        error("$sessionId never became wrapped=$wrapped")
    }

    @Test
    fun `a wrapper session appears at once, gets its hooks, is typed into and ends with Claude`() = testApplication {
        setUp()
        ws().webSocket(ProtocolConstants.PATH_WRAPPER, { header(ProtocolConstants.HEADER_SECRET, secret) }) {
            hello("wrapper-0001")
            awaitWrapped("wrapper-0001")
            // Before any hook: the phone can already send the first prompt.
            val session = agent.registry.session("wrapper-0001")
            assertEquals(ControlMode.WRAPPER, session?.controlMode)
            assertEquals(projectId, session?.projectId)

            hook("UserPromptSubmit", "wrapper-0001")
            assertEquals(SessionState.RUNNING, agent.registry.session("wrapper-0001")?.state)
            assertNull(agent.registry.session("real-1"), "hooks are filed under the terminal's session")

            assertEquals(DeliveryResult.DELIVERED, control.deliverText("wrapper-0001", "run the tests").result)
            assertEquals("run the tests", nextInput())
            assertEquals("\r", nextInput(), "Enter is sent as its own keystroke")
            assertEquals(DeliveryResult.DELIVERED, control.pressKey("wrapper-0001", TerminalKey.SHIFT_TAB).result)
            assertEquals("\u001B[Z", nextInput())

            // Stop from the phone: Esc in the terminal, and the session is no longer shown as running.
            assertEquals(DeliveryResult.DELIVERED, control.quickAction("wrapper-0001", QuickAction.INTERRUPT).result)
            assertEquals("\u001B", nextInput())
            assertEquals(SessionState.IDLE, agent.registry.session("wrapper-0001")?.state)

            send(Frame.Text(WrapperMessage.encode(WrapperMessage.Exit(0))))
        }
        awaitWrapped("wrapper-0001", wrapped = false)
        assertEquals(SessionState.ENDED, agent.registry.session("wrapper-0001")?.state)
    }

    @Test
    fun `hooks without a wrapper header keep Claude's session id and stay hook controlled`() = testApplication {
        setUp()
        hook("UserPromptSubmit", "")
        assertEquals(ControlMode.HOOKS, agent.registry.session("real-1")?.controlMode)
        assertEquals(DeliveryResult.FAILED, control.pressKey("real-1", TerminalKey.ENTER).result)
    }

    @Test
    fun `a wrong secret or the tunnel cannot attach a terminal`() = testApplication {
        setUp()
        ws().webSocket(ProtocolConstants.PATH_WRAPPER, { header(ProtocolConstants.HEADER_SECRET, "nope") }) {
            hello("wrapper-0002")
            assertNull(incoming.receiveCatching().getOrNull())
        }
        ws().webSocket(ProtocolConstants.PATH_WRAPPER, {
            header(ProtocolConstants.HEADER_SECRET, secret)
            header("CF-Connecting-IP", "203.0.113.9")
        }) {
            assertNull(incoming.receiveCatching().getOrNull())
        }
        assertNull(agent.registry.session("wrapper-0002"))
        assertEquals(DeliveryResult.FAILED, control.pressKey("wrapper-0002", TerminalKey.ENTER).result)
    }

    @Test
    fun `an attached phone sees the terminal, and a read-only phone cannot type`() = testApplication {
        setUp()
        val phone = agent.pairDevice(DeviceGrant(canSendInput = false, allProjects = false, projectIds = setOf(projectId)))
        ws().webSocket(ProtocolConstants.PATH_WRAPPER, { header(ProtocolConstants.HEADER_SECRET, secret) }) {
            val wrapper = this
            hello("wrapper-0003")
            send(Frame.Text(WrapperMessage.encode(WrapperMessage.Output("\u001B[32m> ready\u001B[0m\r\n"))))
            awaitWrapped("wrapper-0003")
            ws().webSocket("/ws") {
                with(agent) {
                    assertIs<Message.Ready>(authenticate(phone))
                    sendMessage(Message.TerminalAttach("wrapper-0003"))
                    val screen = withTimeout(5_000) {
                        var found: Message.TerminalScreen? = null
                        while (found == null) found = receiveMessage() as? Message.TerminalScreen
                        found
                    }
                    assertTrue(screen.lines.any { line -> line.spans.joinToString("") { it.text } == "> ready" })

                    sendMessage(Message.TerminalKeyRequest("wrapper-0003", TerminalKey.ENTER))
                    val refusal = withTimeout(5_000) {
                        var found: Message.Error? = null
                        while (found == null) found = receiveMessage() as? Message.Error
                        found
                    }
                    assertEquals(ErrorCode.READ_ONLY, refusal.code)
                }
            }
            wrapper.send(Frame.Text(WrapperMessage.encode(WrapperMessage.Exit(0))))
        }
    }
}
