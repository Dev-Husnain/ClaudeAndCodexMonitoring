package com.claude.codex.ai.monitoring.desktop.control

import com.claude.codex.ai.monitoring.desktop.TestAgent
import com.claude.codex.ai.monitoring.desktop.hooks.HookReceiver
import com.claude.codex.ai.monitoring.desktop.projects.ProjectStore
import com.claude.codex.ai.monitoring.desktop.session.SessionTracker
import com.claude.codex.ai.monitoring.desktop.storage.AppStorage
import com.claude.codex.ai.monitoring.protocol.AwaitingDto
import com.claude.codex.ai.monitoring.protocol.AwaitingKind
import com.claude.codex.ai.monitoring.protocol.ControlMode
import com.claude.codex.ai.monitoring.protocol.Message
import com.claude.codex.ai.monitoring.protocol.QuickAction
import com.claude.codex.ai.monitoring.protocol.SessionDto
import com.claude.codex.ai.monitoring.protocol.SessionState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals

/** What a connected phone sees during Away mode, end to end from the hook posts. */
class AwayModeFlowTest {

    private val agent = TestAgent()
    private val projectDir = Files.createTempDirectory("agentmon-away")
    private val projects = ProjectStore(AppStorage.openDatabase(null)).apply { add(projectDir) }
    private val tracker = SessionTracker(agent.registry, projects)
    private val control = ControlCenter(agent.registry, agent.audit)
    private val secret = "k".repeat(43)
    private val receiver = HookReceiver(secret, tracker, agent.audit, control)

    private fun body(event: String, extra: String = "") =
        """{"session_id":"real-1","hook_event_name":"$event","cwd":${quoted(projectDir.toString())},"transcript_path":"t"$extra}"""

    private fun quoted(s: String) = "\"" + s.replace("\\", "\\\\") + "\""

    private suspend fun post(event: String, extra: String = "") = receiver.receive(secret, viaTunnel = false, body(event, extra))

    private suspend fun waitFor(kind: AwaitingKind) {
        repeat(400) {
            if (agent.registry.session("real-1")?.awaiting?.kind == kind) return
            delay(5)
        }
        error("never held as $kind")
    }

    @Test
    fun `the phone's last update after approve then stop is the reply request`() = runBlocking {
        val seen = Collections.synchronizedList(mutableListOf<SessionDto>())
        val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            agent.registry.updates.collect { if (it is Message.SessionUpdate && it.session.sessionId == "real-1") seen += it.session }
        }
        control.setAwayMode(true, "test")
        post("UserPromptSubmit", ""","prompt":"create hello.txt"""")
        val permission = async(Dispatchers.Default) { post("PermissionRequest", ""","tool_name":"Write"""") }
        waitFor(AwaitingKind.PERMISSION)
        control.quickAction("real-1", QuickAction.APPROVE)
        permission.await()
        post("PostToolUse", ""","tool_name":"Write"""")
        val stop = async(Dispatchers.Default) { post("Stop", ""","last_assistant_message":"done"""") }
        waitFor(AwaitingKind.REPLY)
        delay(50)

        assertEquals(AwaitingKind.REPLY, seen.last().awaiting?.kind)
        control.quickAction("real-1", QuickAction.DENY) // "Let it stop"
        stop.await()
        collector.cancel()
    }

    @Test
    fun `concurrent changes reach the phone in the order the state changed`() = runBlocking {
        agent.registry.upsertSession(SessionDto("race", "p1", SessionState.IDLE, ControlMode.HOOKS, 1, 1))
        repeat(20) { round ->
            val seen = Collections.synchronizedList(mutableListOf<SessionDto>())
            val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                agent.registry.updates.collect { if (it is Message.SessionUpdate && it.session.sessionId == "race") seen += it.session }
            }
            (0 until 16).map { i ->
                async(Dispatchers.Default) {
                    val awaiting = if (i % 2 == 0) AwaitingDto(AwaitingKind.REPLY, "r$round-$i", i.toLong()) else null
                    agent.registry.updateSession("race") { it.copy(awaiting = awaiting, lastEventAt = i.toLong()) }
                }
            }.awaitAll()
            delay(20)
            assertEquals(agent.registry.session("race"), seen.last())
            collector.cancel()
        }
    }
}
