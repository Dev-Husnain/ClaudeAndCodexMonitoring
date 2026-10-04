package com.claude.codex.ai.monitoring.desktop.history

import com.claude.codex.ai.monitoring.desktop.TestAgent
import com.claude.codex.ai.monitoring.desktop.control.ControlCenter
import com.claude.codex.ai.monitoring.protocol.AwaitingKind
import com.claude.codex.ai.monitoring.protocol.ControlMode
import com.claude.codex.ai.monitoring.protocol.DeliveryResult
import com.claude.codex.ai.monitoring.protocol.QuickAction
import com.claude.codex.ai.monitoring.protocol.SessionDto
import com.claude.codex.ai.monitoring.protocol.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Resuming saved conversations from the phone, with a fake `claude` process. */
class ResumeFlowTest {

    /** Stands in for `claude -p`: records stdin, emits [output] lines, exits when told to. */
    private class FakeClaude(private val exitCode: Int = 0) : Process() {
        val stdin = ByteArrayOutputStream()
        private val out = PipedOutputStream()
        private val input = PipedInputStream(out)
        private val exited = CountDownLatch(1)
        var destroyed = false

        fun emit(line: String) = out.write((line + "\n").toByteArray())

        fun exit() {
            out.close()
            exited.countDown()
        }

        override fun getOutputStream(): OutputStream = stdin
        override fun getInputStream(): InputStream = input
        override fun getErrorStream(): InputStream = InputStream.nullInputStream()
        override fun waitFor(): Int {
            exited.await()
            return exitCode
        }
        override fun waitFor(timeout: Long, unit: TimeUnit) = exited.await(timeout, unit)
        override fun exitValue(): Int = if (exited.count == 0L) exitCode else throw IllegalThreadStateException()
        override fun isAlive() = exited.count > 0
        override fun destroy() {
            destroyed = true
            exit()
        }
    }

    private val agent = TestAgent()
    private val config = Files.createTempDirectory("agentmon-claude-config")
    private val project: Path = Files.createTempDirectory("agentmon-project").resolve("app").createDirectories()
    private val claudeId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    private val launched = mutableListOf<List<String>>()
    private var nextProcess = FakeClaude()
    private val runner = HeadlessRunner(
        agent.registry,
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
        resolveCommand = { listOf("claude") + it },
        launch = { command, _ -> launched += command; nextProcess },
    )
    private val control = ControlCenter(agent.registry, agent.audit).also { center ->
        center.headless = runner
        center.resumer = SessionResumer(agent.registry, runner, TranscriptStore(configDir = config), projectPath = { if (it == "p1") project else null })
    }

    init {
        val file = config.resolve("projects").resolve(TranscriptStore.folderName(project)).createDirectories().resolve("$claudeId.jsonl")
        file.writeText("""{"type":"user","message":{"content":"Fix the login bug"}}""" + "\n")
    }

    private suspend fun until(condition: () -> Boolean) {
        repeat(300) {
            if (condition()) return
            delay(10)
        }
        error("condition never became true")
    }

    @Test
    fun `a saved conversation is resumed with the prompt on stdin, never on the command line`() = runBlocking {
        val prompt = "now add tests & \"quote\" | stuff"
        val delivery = control.resume("p1", claudeId, prompt)
        assertEquals(DeliveryResult.DELIVERED, delivery.result)
        assertEquals(claudeId, delivery.detail)

        assertEquals(listOf("claude", "-p", "--resume", claudeId, "--output-format", "stream-json", "--verbose"), launched.single())
        until { nextProcess.stdin.toString(Charsets.UTF_8) == prompt }
        val session = agent.registry.session(claudeId)
        assertEquals(SessionState.RUNNING, session?.state)
        assertEquals(ControlMode.HEADLESS, session?.controlMode)
        assertTrue(agent.registry.history(claudeId).events.any { it.title == "From your phone" && it.detail == prompt })

        nextProcess.emit("""{"type":"result","is_error":false,"result":"done"}""")
        nextProcess.exit()
        until { agent.registry.session(claudeId)?.state == SessionState.ENDED }
    }

    @Test
    fun `a failed run is shown as an error`() = runBlocking {
        control.resume("p1", claudeId, "go")
        nextProcess.emit("""{"type":"result","is_error":true,"result":"Not logged in"}""")
        nextProcess.exit()
        until { agent.registry.session(claudeId)?.state == SessionState.ERROR }
        assertEquals("Not logged in", agent.registry.history(claudeId).events.last().detail)
    }

    @Test
    fun `a conversation still open on the computer is not resumed twice`() {
        agent.registry.upsertSession(SessionDto(claudeId, "p1", SessionState.IDLE, ControlMode.HOOKS, 1, 2))
        assertEquals(DeliveryResult.FAILED, control.resume("p1", claudeId, "go").result)
        assertEquals(DeliveryResult.FAILED, control.resume("p1", "not-a-uuid", "go").result)
        assertEquals(DeliveryResult.FAILED, control.resume("other-project", claudeId, "go").result)
        assertTrue(launched.isEmpty())
    }

    @Test
    fun `typing into an ended wrapper session resumes Claude's conversation under the same session`() = runBlocking {
        agent.registry.upsertSession(SessionDto("wrapper-0001", "p1", SessionState.ENDED, ControlMode.HOOKS, 1, 2, claudeSessionId = claudeId))
        assertEquals(DeliveryResult.DELIVERED, control.deliverText("wrapper-0001", "continue please").result)
        assertTrue(claudeId in launched.single())
        // Hooks of the resumed run carry Claude's id; they belong to the terminal's session.
        assertEquals("wrapper-0001", runner.sessionIdFor(claudeId))
        nextProcess.exit()
        until { runner.sessionIdFor(claudeId) == null }
    }

    @Test
    fun `permission prompts of a phone-started run wait for the phone, and Stop ends the run`() = runBlocking {
        control.resume("p1", claudeId, "go")
        val permission = async { control.onPermissionRequest(claudeId, "Bash: rm -rf build") }
        until { agent.registry.session(claudeId)?.awaiting?.kind == AwaitingKind.PERMISSION }
        assertEquals(DeliveryResult.DELIVERED, control.quickAction(claudeId, QuickAction.APPROVE).result)
        assertNotNull(permission.await(), "answered with a decision although Away mode is off")

        assertEquals(DeliveryResult.DELIVERED, control.quickAction(claudeId, QuickAction.INTERRUPT).result)
        assertTrue(nextProcess.destroyed)
        until { agent.registry.session(claudeId)?.state == SessionState.ENDED }
        assertFalse(agent.registry.history(claudeId).events.any { it.title == "Resuming failed" }, "a stop is not a failure")
    }
}
