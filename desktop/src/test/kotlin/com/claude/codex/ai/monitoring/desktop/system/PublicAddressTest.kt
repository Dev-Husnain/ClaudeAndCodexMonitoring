package com.claude.codex.ai.monitoring.desktop.system

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublicAddressTest {

    @Test
    fun `a hostname or https address is accepted and cleaned up`() {
        assertEquals(PublicAddress.Parsed.Valid("https://agent.example.com"), PublicAddress.parse(" agent.example.com "))
        assertEquals(PublicAddress.Parsed.Valid("https://agent.example.com"), PublicAddress.parse("https://Agent.Example.com/"))
        assertEquals(PublicAddress.Parsed.Valid("https://agent.example.com:8443"), PublicAddress.parse("https://agent.example.com:8443"))
        assertEquals(PublicAddress.Parsed.Cleared, PublicAddress.parse("  "))
    }

    @Test
    fun `plain http, local addresses, paths and junk are refused`() {
        listOf("http://agent.example.com", "localhost", "https://127.0.0.1", "agent.example.com/ws", "not a host", "https://user@agent.example.com", "example")
            .forEach { assertIs<PublicAddress.Parsed.Invalid>(PublicAddress.parse(it), it) }
    }

    @Test
    fun `the saved address is remembered, and an override wins and cannot be edited`() {
        val file = Files.createTempDirectory("agentmon-address").resolve("settings.properties")
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val options = ComputerOptions(DesktopSettings(file), KeepAwake { }, AutoStart(isWindows = false), scope)
        assertNull(options.publicUrl.value, "nothing set: USB only")
        assertFalse(options.setPublicUrl("http://agent.example.com"))
        assertTrue(options.addressError.value!!.contains("https"))
        assertTrue(options.setPublicUrl("agent.example.com"))
        assertNull(options.addressError.value)
        assertEquals("https://agent.example.com", ComputerOptions(DesktopSettings(file), KeepAwake { }, AutoStart(isWindows = false), scope).publicUrl.value)
        assertTrue(options.setPublicUrl(""))
        assertNull(DesktopSettings(file).publicUrl.value)

        val locked = ComputerOptions(
            DesktopSettings(file), KeepAwake { }, AutoStart(isWindows = false), scope,
            ComputerOptions.PublicUrlOverride("https://env.example.com", "AGENTMON_PUBLIC_URL"),
        )
        assertEquals("https://env.example.com", locked.publicUrl.value)
        assertEquals("AGENTMON_PUBLIC_URL", locked.publicUrlLockedBy)
        assertFalse(locked.setPublicUrl("agent.example.com"))
    }
}
