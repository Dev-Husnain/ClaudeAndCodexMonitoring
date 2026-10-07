package com.claude.codex.ai.monitoring.desktop.control

import com.claude.codex.ai.monitoring.desktop.TestAgent
import com.claude.codex.ai.monitoring.protocol.AwaitingKind
import com.claude.codex.ai.monitoring.protocol.DeliveryResult
import com.claude.codex.ai.monitoring.protocol.QuickAction
import com.claude.codex.ai.monitoring.protocol.SessionState
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ControlCenterTest {

    private val agent = TestAgent()
    private val control = ControlCenter(agent.registry, agent.audit)

    private suspend fun waitUntilHeld(kind: AwaitingKind) {
        repeat(200) {
            if (agent.registry.session("s1")?.awaiting?.kind == kind) return
            delay(5)
        }
        error("session never became held")
    }

    private fun decision(json: String?) =
        Json.parseToJsonElement(json!!).jsonObject.getValue("hookSpecificOutput").jsonObject

    /** The instruction a Stop reply gives Claude, after checking the reply keeps Claude from stopping. */
    private fun continuation(json: String?): String {
        val reply = Json.parseToJsonElement(json!!).jsonObject
        assertEquals("block", reply.getValue("decision").jsonPrimitive.content)
        return reply.getValue("reason").jsonPrimitive.content
    }

    @Test
    fun `away mode off answers every hook at once with no decision`() = runBlocking {
        assertNull(control.onPermissionRequest("s1", "Bash: ls"))
        assertNull(control.onStop("s1", "done"))
    }

    @Test
    fun `a held permission request is approved from the phone`() = runBlocking {
        control.setAwayMode(true, "test")
        val reply = async { control.onPermissionRequest("s1", "Bash: rm -rf build") }
        waitUntilHeld(AwaitingKind.PERMISSION)
        assertEquals("Bash: rm -rf build", agent.registry.session("s1")?.awaiting?.detail)

        assertEquals(DeliveryResult.DELIVERED, control.quickAction("s1", QuickAction.APPROVE).result)
        val out = decision(reply.await())
        assertEquals("allow", out.getValue("decision").jsonObject.getValue("behavior").jsonPrimitive.content)
        assertNull(agent.registry.session("s1")?.awaiting)
    }

    @Test
    fun `deny and stop map to the documented decision`() = runBlocking {
        control.setAwayMode(true, "test")
        val reply = async { control.onPermissionRequest("s1", "Edit") }
        waitUntilHeld(AwaitingKind.PERMISSION)
        control.quickAction("s1", QuickAction.INTERRUPT)
        val decision = decision(reply.await()).getValue("decision").jsonObject
        assertEquals("deny", decision.getValue("behavior").jsonPrimitive.content)
        assertEquals("true", decision.getValue("interrupt").jsonPrimitive.content)
    }

    @Test
    fun `a reply typed on the phone continues Claude after it stops`() = runBlocking {
        control.setAwayMode(true, "test")
        val reply = async { control.onStop("s1", "All tests pass.") }
        waitUntilHeld(AwaitingKind.REPLY)
        assertEquals(DeliveryResult.DELIVERED, control.deliverText("s1", "Now update the README").result)
        assertTrue(continuation(reply.await()).endsWith("Now update the README"))
        assertTrue(agent.registry.history("s1").events.any { it.title == "From your phone" })
    }

    @Test
    fun `a message sent while Claude works is queued for the next stop, even without away mode`() = runBlocking {
        assertEquals(DeliveryResult.QUEUED, control.deliverText("s1", "Also add tests").result)
        val reply = control.onStop("s1", "done")
        assertTrue(continuation(reply).endsWith("Also add tests"))
        assertNull(control.onStop("s1", "done again"), "the queued message is delivered once")
    }

    @Test
    fun `turning away mode off releases everything that was held`() = runBlocking {
        control.setAwayMode(true, "test")
        val permission = async { control.onPermissionRequest("s1", "Bash") }
        waitUntilHeld(AwaitingKind.PERMISSION)
        control.setAwayMode(false, "test")
        assertNull(permission.await())
        assertNull(agent.registry.session("s1")?.awaiting)
    }

    @Test
    fun `answers without anything held fail clearly`() = runBlocking {
        assertEquals(DeliveryResult.FAILED, control.quickAction("s1", QuickAction.APPROVE).result)
        assertEquals(DeliveryResult.FAILED, control.deliverText("missing", "hi").result)
    }

    @Test
    fun `stop from the phone stops a working session at its next hook, once`() {
        assertEquals(DeliveryResult.QUEUED, control.quickAction("s1", QuickAction.INTERRUPT).result)
        val reply = Json.parseToJsonElement(control.takeStopRequest("s1", "PreToolUse")!!).jsonObject
        assertEquals("false", reply.getValue("continue").jsonPrimitive.content)
        assertEquals(SessionState.IDLE, agent.registry.session("s1")?.state)
        assertTrue(agent.registry.history("s1").events.any { it.title == "Stopped from your phone" })
        assertNull(control.takeStopRequest("s1", "PreToolUse"), "the request is used up")
    }

    @Test
    fun `a stop request does not outlive a turn that ended by itself`() {
        control.quickAction("s1", QuickAction.INTERRUPT)
        assertNull(control.takeStopRequest("s1", "StopFailure"))
        assertNull(control.takeStopRequest("s1", "UserPromptSubmit"), "the next turn is not stopped")
    }

    @Test
    fun `stopping an idle session fails`() {
        agent.registry.updateSession("s1") { it.copy(state = SessionState.IDLE) }
        assertEquals(DeliveryResult.FAILED, control.quickAction("s1", QuickAction.INTERRUPT).result)
        assertNull(control.takeStopRequest("s1", "UserPromptSubmit"))
    }
}
