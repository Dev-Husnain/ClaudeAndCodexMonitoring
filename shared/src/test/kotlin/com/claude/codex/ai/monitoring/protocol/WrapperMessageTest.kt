package com.claude.codex.ai.monitoring.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WrapperMessageTest {

    @Test
    fun `wrapper frames survive a round trip`() {
        listOf(
            WrapperMessage.Hello("w1", "C:\\work\\app", 120, 40),
            WrapperMessage.Output("\u001B[32mok\u001B[0m ✓"),
            WrapperMessage.Resize(100, 30),
            WrapperMessage.Exit(0),
            WrapperMessage.Input("\u001B[200~two\nlines\u001B[201~\r"),
        ).forEach { message ->
            assertEquals(message, WrapperMessage.decode(WrapperMessage.encode(message)))
        }
    }

    @Test
    fun `frames carry a type field and junk is rejected`() {
        assertTrue(WrapperMessage.encode(WrapperMessage.Exit(1)).contains("\"type\":\"exit\""))
        assertNull(WrapperMessage.decode("not json"))
        assertNull(WrapperMessage.decode("""{"type":"unknown"}"""))
    }
}
