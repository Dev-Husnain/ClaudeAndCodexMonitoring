package com.claude.codex.ai.monitoring.desktop.hooks

import com.claude.codex.ai.monitoring.desktop.TestAgent
import com.claude.codex.ai.monitoring.desktop.projects.ProjectStore
import com.claude.codex.ai.monitoring.desktop.security.RateLimiter
import com.claude.codex.ai.monitoring.desktop.server.agentModule
import com.claude.codex.ai.monitoring.desktop.session.SessionTracker
import com.claude.codex.ai.monitoring.desktop.storage.AppStorage
import com.claude.codex.ai.monitoring.protocol.SessionState
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HookEndpointTest {

    private val agent = TestAgent()
    private val projectDir = Files.createTempDirectory("agentmon-hook")
    private val projects = ProjectStore(AppStorage.openDatabase(null)).apply { add(projectDir) }
    private val tracker = SessionTracker(agent.registry, projects)
    private val secret = "k".repeat(43)
    private val receiver = HookReceiver(secret, tracker, agent.audit)

    private fun ApplicationTestBuilder.setUp() {
        application { agentModule(agent.handler, agent.pairing, RateLimiter(), "test", receiver) }
    }

    /** A hook body whose cwd is inside the monitored project, like Claude Code sends it. */
    private fun body(event: String, extra: String = "") =
        """{"session_id":"real-1","hook_event_name":"$event","cwd":${quoted(projectDir.resolve("app").toString())},"transcript_path":"t"$extra}"""

    private fun quoted(s: String) = "\"" + s.replace("\\", "\\\\") + "\""

    @Test
    fun `a valid hook updates the session and answers with an empty 2xx`() = testApplication {
        setUp()
        val response = client.post("/hook") {
            header(HookInstaller.SECRET_HEADER, secret)
            setBody(body("PermissionRequest", ""","tool_name":"Bash","tool_input":{"command":"rm -rf build"}"""))
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals("", response.bodyAsText())
        assertEquals(SessionState.WAITING_INPUT, agent.registry.session("real-1")?.state)
    }

    @Test
    fun `wrong or missing secret is refused and changes nothing`() = testApplication {
        setUp()
        assertEquals(HttpStatusCode.Forbidden, client.post("/hook") { setBody(body("Stop")) }.status)
        assertEquals(
            HttpStatusCode.Forbidden,
            client.post("/hook") { header(HookInstaller.SECRET_HEADER, "wrong"); setBody(body("Stop")) }.status,
        )
        assertNull(agent.registry.session("real-1"))
    }

    @Test
    fun `hooks arriving through the tunnel are refused even with the secret`() = testApplication {
        setUp()
        val response = client.post("/hook") {
            header(HookInstaller.SECRET_HEADER, secret)
            header("CF-Connecting-IP", "203.0.113.9")
            setBody(body("Stop"))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertNull(agent.registry.session("real-1"))
        assertTrue(agent.audit.entries.value.any { it.message.contains("tunnel") })
    }

    @Test
    fun `hooks forwarded by any other tunnel or proxy are refused too`() = testApplication {
        setUp()
        listOf("X-Forwarded-For" to "198.51.100.7", "X-Real-IP" to "198.51.100.7", "Forwarded" to "for=198.51.100.7").forEach { (name, value) ->
            val response = client.post("/hook") {
                header(HookInstaller.SECRET_HEADER, secret)
                header(name, value)
                setBody(body("Stop"))
            }
            assertEquals(HttpStatusCode.Forbidden, response.status, name)
        }
        assertNull(agent.registry.session("real-1"))
    }

    @Test
    fun `events from folders that are not monitored are ignored`() = testApplication {
        setUp()
        val response = client.post("/hook") {
            header(HookInstaller.SECRET_HEADER, secret)
            setBody("""{"session_id":"other","hook_event_name":"Stop","cwd":"Z:\\elsewhere"}""")
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
        assertNull(agent.registry.session("other"))
    }

    @Test
    fun `malformed bodies are rejected`() = testApplication {
        setUp()
        val response = client.post("/hook") { header(HookInstaller.SECRET_HEADER, secret); setBody("not json") }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
