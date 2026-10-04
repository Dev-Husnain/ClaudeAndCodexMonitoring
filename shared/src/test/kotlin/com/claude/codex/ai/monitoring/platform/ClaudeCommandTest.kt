package com.claude.codex.ai.monitoring.platform

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClaudeCommandTest {

    private fun existing(vararg paths: String): (File) -> Boolean = { it.path in paths.map { p -> File(p).path } }

    @Test
    fun `an npm cmd shim on Windows is started through cmd`() {
        val env = mapOf("Path" to "C:\\npm;C:\\tools", "PATHEXT" to ".COM;.EXE;.BAT;.CMD", "ComSpec" to "C:\\Windows\\cmd.exe")
        val command = ClaudeCommand.resolve(listOf("--resume"), env, isWindows = true, exists = existing("C:\\npm\\claude.cmd", "C:\\tools\\claude.exe"))
        assertEquals(listOf("C:\\Windows\\cmd.exe", "/d", "/c", "claude", "--resume"), command)
    }

    @Test
    fun `PATH order wins, like in the shell`() {
        val env = mapOf("PATH" to "C:\\tools;C:\\npm", "PATHEXT" to ".EXE;.CMD")
        val command = ClaudeCommand.resolve(emptyList(), env, isWindows = true, exists = existing("C:\\npm\\claude.cmd", "C:\\tools\\claude.exe"))
        assertEquals(listOf(File("C:\\tools\\claude.exe").path), command)
    }

    @Test
    fun `AGENTMON_CLAUDE overrides the search, and a missing claude is reported`() {
        val env = mapOf("PATH" to "/usr/bin", "AGENTMON_CLAUDE" to "/opt/claude/bin/claude")
        assertEquals(listOf(File("/opt/claude/bin/claude").path, "-p", "hi"), ClaudeCommand.resolve(listOf("-p", "hi"), env, isWindows = false, exists = existing("/opt/claude/bin/claude")))
        assertNull(ClaudeCommand.resolve(emptyList(), mapOf("PATH" to "/usr/bin"), isWindows = false, exists = { false }))
    }
}
