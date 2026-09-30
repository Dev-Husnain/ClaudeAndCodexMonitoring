package com.claude.codex.ai.monitoring.desktop.control

import com.claude.codex.ai.monitoring.desktop.TestAgent
import com.claude.codex.ai.monitoring.protocol.AwaitingKind
import com.claude.codex.ai.monitoring.protocol.DeliveryResult
import com.claude.codex.ai.monitoring.protocol.QuickAction
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
        val context = decision(reply.await()).getValue("additionalContext").jsonPrimitive.content
        assertTrue(context.endsWith("Now update the README"))
        assertTrue(agent.registry.history("s1").events.any { it.title == "From your phone" })
    }

    @Test
    fun `a message sent while Claude works is queued for the next stop, even without away mode`() = runBlocking {
        assertEquals(DeliveryResult.QUEUED, control.deliverText("s1", "Also add tests").result)
        val reply = control.onStop("s1", "done")
        assertTrue(decision(reply).getValue("additionalContext").jsonPrimitive.content.endsWith("Also add tests"))
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
    fun `answers without anything held fail clearly`() {
        assertEquals(DeliveryResult.FAILED, control.quickAction("s1", QuickAction.APPROVE).result)
        assertEquals(DeliveryResult.FAILED, control.deliverText("missing", "hi").result)
    }
}
