package com.claude.codex.ai.monitoring.desktop.wrapper

import com.claude.codex.ai.monitoring.desktop.TestAgent
import com.claude.codex.ai.monitoring.desktop.control.ControlCenter
import com.claude.codex.ai.monitoring.desktop.history.HeadlessRunner
import com.claude.codex.ai.monitoring.desktop.history.SessionResumer
import com.claude.codex.ai.monitoring.desktop.history.TranscriptStore
import com.claude.codex.ai.monitoring.protocol.ControlMode
import com.claude.codex.ai.monitoring.protocol.DeliveryResult
import com.claude.codex.ai.monitoring.protocol.SessionDto
import com.claude.codex.ai.monitoring.protocol.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerminalLauncherTest {

    private val agent = TestAgent()
    private val config = Files.createTempDirectory("agentmon-claude-config")
    private val project: Path = Files.createTempDirectory("agentmon-start").resolve("shop").createDirectories()
    private val scripts = Files.createTempDirectory("agentmon-launch")
    private val wrapper: Path = Files.createTempDirectory("agentmon-cli").resolve("agentmon.bat").also { it.writeText("@echo off") }
    private val claudeId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    private val started = mutableListOf<Pair<List<String>, Map<String, String>>>()
    private val launcher = TerminalLauncher(scripts, agentmon = { wrapper }, isWindows = true, run = { command, env -> started += command to env })
    private val runner = HeadlessRunner(agent.registry, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    private val control = ControlCenter(agent.registry, agent.audit).also { center ->
        center.resumer = SessionResumer(
            agent.registry, runner, TranscriptStore(configDir = config),
            projectPath = { if (it == "p1") project else null },
            launcher = launcher,
            projectName = { "Shop & \"Co\"" },
        )
    }

    init {
        config.resolve("projects").resolve(TranscriptStore.folderName(project)).createDirectories()
            .resolve("$claudeId.jsonl").writeText("""{"type":"user","message":{"content":"Fix the login bug"}}""" + "\n")
    }

    @Test
    fun `an ended conversation is continued in a new terminal and its session exists at once`() {
        val delivery = control.startTerminal("p1", claudeId, by = "Pixel")
        assertEquals(DeliveryResult.DELIVERED, delivery.result)
        val sessionId = assertNotNull(delivery.detail)
        val session = assertNotNull(agent.registry.session(sessionId))
        assertEquals(claudeId, session.claudeSessionId)

        val (command, environment) = started.single()
        assertEquals(listOf("cmd.exe", "/c", "start", ""), command.take(4))
        val script = Path.of(command.last()).readText()
        assertTrue(script.contains("call \"$wrapper\" --session $sessionId claude --resume $claudeId"), script)
        assertTrue(script.contains("title Shop  Co (AgentMon)"), "the title keeps only safe characters: $script")
        assertTrue(environment.keys.none { it.startsWith("CLAUDE", ignoreCase = true) })
    }

    @Test
    fun `a new conversation needs no id, and bad requests change nothing`() {
        val before = agent.registry.state.value.sessions.size
        val fresh = control.startTerminal("p1", null, by = "Pixel")
        assertEquals(DeliveryResult.DELIVERED, fresh.result)
        assertTrue(Path.of(started.single().first.last()).readText().contains(" claude\r\n"))

        assertEquals(DeliveryResult.FAILED, control.startTerminal("p1", "not-a-uuid & calc", by = "x").result)
        assertEquals(DeliveryResult.FAILED, control.startTerminal("other", null, by = "x").result)
        assertEquals(1, started.size)
        assertEquals(before + 1, agent.registry.state.value.sessions.size, "only the started session was added")
    }

    @Test
    fun `a conversation that is still open is not started twice`() {
        agent.registry.mutateSession("running") { SessionDto("running", "p1", SessionState.RUNNING, ControlMode.WRAPPER, 1, 1, claudeSessionId = claudeId) }
        assertEquals(DeliveryResult.FAILED, control.startTerminal("p1", claudeId, by = "x").result)
        assertTrue(started.isEmpty())
    }

    @Test
    fun `a missing wrapper or another system is reported, not attempted`() {
        val noWrapper = TerminalLauncher(scripts, agentmon = { null }, isWindows = true, run = { _, _ -> error("must not run") })
        assertTrue(noWrapper.launch(project, "wrapper-0001", emptyList(), "x")!!.contains("agentmon"))
        val mac = TerminalLauncher(scripts, agentmon = { wrapper }, isWindows = false, run = { _, _ -> error("must not run") })
        assertNotNull(mac.launch(project, "wrapper-0001", emptyList(), "x"))
        assertNull(launcher.launch(project, "wrapper-0001", emptyList(), "x"))
    }
}
