package com.claude.codex.ai.monitoring.desktop.session

import com.claude.codex.ai.monitoring.desktop.hooks.HookEventDto
import com.claude.codex.ai.monitoring.protocol.EventKind
import com.claude.codex.ai.monitoring.protocol.SessionDto
import com.claude.codex.ai.monitoring.protocol.SessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionStateMachineTest {

    private fun hook(json: String): HookEventDto = HookEventDto.json.decodeFromString(HookEventDto.serializer(), json)

    private fun apply(current: SessionDto?, json: String, now: Long = 1_000) =
        SessionStateMachine.apply(current, hook(json), "p1", now) { "e" }

    private val base = """"session_id":"s1","cwd":"C:\\proj","transcript_path":"t""""

    @Test
    fun `a prompt creates a running session with the prompt in the timeline`() {
        val t = apply(null, """{$base,"hook_event_name":"UserPromptSubmit","prompt":"Fix the build\nplease"}""")!!
        assertEquals(SessionState.RUNNING, t.session.state)
        assertEquals("Fix the build", t.session.lastMessageSnippet)
        assertEquals(EventKind.PROMPT, t.event?.kind)
        assertEquals("p1", t.session.projectId)
    }

    @Test
    fun `tool use records the tool and a readable summary`() {
        val t = apply(null, """{$base,"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"./gradlew test","description":"Run tests"},"tool_use_id":"x"}""")!!
        assertEquals("Bash", t.session.lastTool)
        assertEquals("./gradlew test", t.event?.detail)
    }

    @Test
    fun `permission request waits for input and the later notification is not logged twice`() {
        val waiting = apply(null, """{$base,"hook_event_name":"PermissionRequest","tool_name":"Bash","tool_input":{"command":"rm -rf build"}}""")!!
        assertEquals(SessionState.WAITING_INPUT, waiting.session.state)
        assertEquals("Bash: rm -rf build", waiting.event?.detail)
        val notification = apply(waiting.session, """{$base,"hook_event_name":"Notification","notification_type":"permission_prompt","message":"Claude needs your permission to use Bash"}""")!!
        assertEquals(SessionState.WAITING_INPUT, notification.session.state)
        assertNull(notification.event)
    }

    @Test
    fun `a tool finishing after approval returns to running`() {
        val waiting = apply(null, """{$base,"hook_event_name":"PermissionRequest","tool_name":"Bash"}""")!!
        val after = apply(waiting.session, """{$base,"hook_event_name":"PostToolUse","tool_name":"Bash","tool_input":{}}""")!!
        assertEquals(SessionState.RUNNING, after.session.state)
        assertNull(after.event)
    }

    @Test
    fun `stop, failure and end states`() {
        val stopped = apply(null, """{$base,"hook_event_name":"Stop","last_assistant_message":"Done. Tests pass."}""")!!
        assertEquals(SessionState.IDLE, stopped.session.state)
        assertEquals("Done. Tests pass.", stopped.session.lastMessageSnippet)

        val failed = apply(null, """{$base,"hook_event_name":"StopFailure","error":"rate_limit","error_details":"429 Too Many Requests"}""")!!
        assertEquals(SessionState.ERROR, failed.session.state)
        assertEquals("rate_limit: 429 Too Many Requests", failed.session.errorInfo)

        val ended = apply(stopped.session, """{$base,"hook_event_name":"SessionEnd","reason":"prompt_input_exit"}""")!!
        assertEquals(SessionState.ENDED, ended.session.state)
    }

    @Test
    fun `unknown events and fields are tolerated`() {
        assertNull(apply(null, """{$base,"hook_event_name":"PreCompact","trigger":"auto"}"""))
        val t = apply(null, """{$base,"hook_event_name":"UserPromptSubmit","prompt":"hi","brand_new_field":{"x":1}}""")
        assertEquals(SessionState.RUNNING, t?.session?.state)
    }

    @Test
    fun `only silent running sessions become stale`() {
        val running = apply(null, """{$base,"hook_event_name":"UserPromptSubmit","prompt":"hi"}""", now = 0)!!.session
        assertNull(SessionStateMachine.markStale(running, nowMs = 60_000, staleAfterMs = 120_000))
        assertEquals(SessionState.STALE, SessionStateMachine.markStale(running, nowMs = 200_000, staleAfterMs = 120_000)?.state)
        assertNull(SessionStateMachine.markStale(running.copy(state = SessionState.IDLE), nowMs = 200_000, staleAfterMs = 120_000))
    }
}
