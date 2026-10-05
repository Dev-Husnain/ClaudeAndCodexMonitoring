package com.claude.codex.ai.monitoring.desktop.system

import com.claude.codex.ai.monitoring.protocol.AwaitingDto
import com.claude.codex.ai.monitoring.protocol.AwaitingKind
import com.claude.codex.ai.monitoring.protocol.ControlMode
import com.claude.codex.ai.monitoring.protocol.SessionDto
import com.claude.codex.ai.monitoring.protocol.SessionState
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComputerOptionsTest {

    private fun session(state: SessionState, awaiting: AwaitingDto? = null) =
        SessionDto("s1", "p1", state, ControlMode.HOOKS, 1, 2, awaiting = awaiting)

    @Test
    fun `the computer stays awake only while it matters, and only when allowed`() {
        val calls = mutableListOf<Boolean>()
        val keepAwake = KeepAwake { calls += it }

        keepAwake.update(enabled = true, awayMode = false, sessions = listOf(session(SessionState.IDLE)))
        assertEquals(emptyList(), calls, "idle: nothing to do")
        keepAwake.update(enabled = true, awayMode = false, sessions = listOf(session(SessionState.RUNNING)))
        keepAwake.update(enabled = true, awayMode = false, sessions = listOf(session(SessionState.RUNNING)))
        assertEquals(listOf(true), calls, "asked once, not on every update")
        keepAwake.update(enabled = true, awayMode = false, sessions = listOf(session(SessionState.IDLE)))
        keepAwake.update(enabled = true, awayMode = false, sessions = listOf(session(SessionState.IDLE, AwaitingDto(AwaitingKind.REPLY, null, 1))))
        keepAwake.update(enabled = false, awayMode = true, sessions = listOf(session(SessionState.RUNNING)))
        keepAwake.update(enabled = true, awayMode = true, sessions = emptyList())
        assertEquals(listOf(true, false, true, false, true), calls)
    }

    private class FakeRunKey : RunKey {
        var value: String? = null
        override fun get() = value
        override fun set(command: String) {
            value = command
        }
        override fun remove() {
            value = null
        }
    }

    @Test
    fun `starting with Windows registers the installed copy in the per-user Run key, and can be undone`() {
        val key = FakeRunKey()
        val launch = AutoStart.InstalledLaunch(Path.of("""C:\jdk\bin\javaw.exe"""), Path.of("""C:\Users\me\AppData\Local\AgentMon\agent\lib"""))
        val autoStart = AutoStart(runKey = key, currentLaunch = { launch }, isWindows = true)
        autoStart.enable()
        assertEquals(
            """"C:\jdk\bin\javaw.exe" --enable-native-access=ALL-UNNAMED -cp "C:\Users\me\AppData\Local\AgentMon\agent\lib\*" """ +
                "com.claude.codex.ai.monitoring.desktop.MainKt --background",
            key.value,
        )
        assertTrue(autoStart.isEnabled())
        autoStart.disable()
        assertFalse(autoStart.isEnabled())
    }

    @Test
    fun `a copy running from Gradle cannot be registered, and other systems are not offered it`() {
        val notInstalled = AutoStart(runKey = FakeRunKey(), currentLaunch = { null }, isWindows = true)
        assertTrue(assertFailsWith<AutoStartException> { notInstalled.enable() }.message!!.contains("install-agent"))
        val linux = AutoStart(runKey = FakeRunKey(), currentLaunch = { null }, isWindows = false)
        assertFalse(linux.supported)
        assertFalse(linux.isEnabled())
        linux.disable()
    }

    @Test
    fun `stay awake is on by default and the choice is remembered`() {
        val file = Files.createTempDirectory("agentmon-settings").resolve("settings.properties")
        assertTrue(DesktopSettings(file).keepAwake.value)
        DesktopSettings(file).setKeepAwake(false)
        assertFalse(DesktopSettings(file).keepAwake.value)
    }
}
