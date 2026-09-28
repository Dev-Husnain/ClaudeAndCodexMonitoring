package com.claude.codex.ai.monitoring.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProtocolCodecTest {

    private val codec = ProtocolCodec(clock = { 1_760_000_000_000 }, newId = { "id-1" })

    private val session = SessionDto(
        sessionId = "s1",
        projectId = "p1",
        state = SessionState.WAITING_INPUT,
        controlMode = ControlMode.WRAPPER,
        startedAt = 1L,
        lastEventAt = 2L,
        lastMessageSnippet = "Allow edit?",
        lastTool = "Edit",
    )
    private val event = TimelineEventDto("e1", "s1", 3L, EventKind.TOOL_USE, "Edit", "app/Main.kt")

    private val allMessages: List<Message> = listOf(
        Message.Hello("device", "1.0"),
        Message.Auth("sig"),
        Message.Subscribe(listOf("p1", "p2")),
        Message.SessionList,
        Message.SessionHistory("s1", beforeTs = 10L),
        Message.SessionHistory("s1"),
        Message.SendInput("s1", "continue please"),
        Message.QuickActionRequest("s1", QuickAction.APPROVE),
        Message.TerminalAttach("s1"),
        Message.TerminalDetach,
        Message.Ping,
        Message.Challenge("nonce", "dsig"),
        Message.Ready(ComputerDto("c1", "Laptop"), listOf(ProjectDto("p1", "App")), listOf(session)),
        Message.SessionUpdate(session),
        Message.SessionEvent("s1", event),
        Message.SessionRemoved("s1", "p1"),
        Message.SessionHistoryResult("s1", listOf(event), hasMore = false),
        Message.TerminalChunk("s1", "\u001B[32mok\u001B[0m"),
        Message.Ack("a1", DeliveryResult.DELIVERED),
        Message.Error(ErrorCode.READ_ONLY, "Read-only device", ackId = "a2"),
        Message.Pong,
        Message.Revoked,
    )

    @Test
    fun `every message survives an encode decode round trip`() {
        allMessages.forEach { message ->
            val frame = codec.decode(codec.encode(message, projectId = "p1")).getOrThrow()
            assertEquals(message, frame.message)
            assertEquals("p1", frame.projectId)
            assertEquals("id-1", frame.id)
        }
    }

    @Test
    fun `envelope has spec shape with type outside payload`() {
        val raw = Json.parseToJsonElement(codec.encode(Message.SessionUpdate(session), "p1")).jsonObject
        assertEquals(1, raw.getValue("v").jsonPrimitive.int)
        assertEquals("session.update", raw.getValue("type").jsonPrimitive.content)
        assertEquals(1_760_000_000_000, raw.getValue("ts").jsonPrimitive.content.toLong())
        assertFalse("type" in raw.getValue("payload").jsonObject)
        assertTrue("session" in raw.getValue("payload").jsonObject)
    }

    @Test
    fun `unknown type is a failure instead of a crash`() {
        val result = codec.decode("""{"v":1,"id":"x","type":"nope","ts":1,"payload":{}}""")
        assertTrue(result.isFailure)
    }

    @Test
    fun `newer protocol version is rejected`() {
        val result = codec.decode("""{"v":2,"id":"x","type":"ping","ts":1,"payload":{}}""")
        assertTrue(result.isFailure)
    }

    @Test
    fun `malformed json is a failure`() {
        assertTrue(codec.decode("{not json").isFailure)
    }

    @Test
    fun `unknown payload fields are ignored for forward compatibility`() {
        val frame = codec.decode("""{"v":1,"id":"x","type":"auth","ts":1,"payload":{"signature":"s","extra":true}}""")
        assertEquals(Message.Auth("s"), frame.getOrThrow().message)
    }
}
