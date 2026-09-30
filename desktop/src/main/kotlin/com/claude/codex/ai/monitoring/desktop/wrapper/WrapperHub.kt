package com.claude.codex.ai.monitoring.desktop.wrapper

import com.claude.codex.ai.monitoring.desktop.session.SessionRegistry
import com.claude.codex.ai.monitoring.protocol.ControlMode
import com.claude.codex.ai.monitoring.protocol.Message
import com.claude.codex.ai.monitoring.protocol.SessionDto
import com.claude.codex.ai.monitoring.protocol.SessionState
import com.claude.codex.ai.monitoring.protocol.TerminalKey
import com.claude.codex.ai.monitoring.protocol.WrapperMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Types into wrapper-started sessions. Implemented by [WrapperHub]; lets the control code be tested without one. */
interface WrapperInput {
    fun isWrapped(sessionId: String): Boolean

    /** Types [text] into Claude's input and submits it. */
    fun type(sessionId: String, text: String): Boolean

    fun press(sessionId: String, key: TerminalKey): Boolean
}

/**
 * The `agentmon claude` wrappers connected to this agent (phase 5b). Each wrapper runs Claude in a
 * pseudo-terminal and streams its output here. A terminal is one session on the phone, with the
 * wrapper id as its session id: it appears as soon as the wrapper connects (so the phone can send
 * the first prompt), and Claude's hooks, which carry the wrapper id in a header, are filed under it
 * (see [sessionIdFor]), also across `/clear`. These sessions are `WRAPPER` controlled: the phone can
 * type into them at any time and see their terminal.
 */
class WrapperHub(
    private val registry: SessionRegistry,
    private val scope: CoroutineScope,
    /** The monitored project a working directory belongs to, or null. */
    private val projectFor: (cwd: String) -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val submitDelayMs: Long = SUBMIT_DELAY_MS,
) : WrapperInput {

    private class Link(val wrapperId: String, val mirror: TerminalMirror) {
        val outgoing = Channel<WrapperMessage>(OUTGOING_CAPACITY)
    }

    private val links = ConcurrentHashMap<String, Link>()

    /**
     * Registers a connected wrapper and returns its outgoing frames. Its session is created (or, after an
     * agent restart, taken over) right away when the folder is monitored.
     */
    fun connect(hello: WrapperMessage.Hello): Channel<WrapperMessage> {
        val link = Link(hello.wrapperId, TerminalMirror(hello.columns, hello.rows))
        links.put(hello.wrapperId, link)?.let { old ->
            old.mirror.close()
            old.outgoing.close()
        }
        val projectId = projectFor(hello.cwd)
        registry.mutateSession(hello.wrapperId) { current ->
            when {
                current != null -> current.takeIf { it.controlMode != ControlMode.WRAPPER }?.copy(controlMode = ControlMode.WRAPPER)
                projectId == null -> null
                else -> clock().let { now -> SessionDto(hello.wrapperId, projectId, SessionState.IDLE, ControlMode.WRAPPER, now, now) }
            }
        }
        return link.outgoing
    }

    /** A frame from wrapper [wrapperId]. */
    fun onFrame(wrapperId: String, message: WrapperMessage) {
        val link = links[wrapperId] ?: return
        when (message) {
            is WrapperMessage.Output -> link.mirror.feed(message.data)
            is WrapperMessage.Resize -> link.mirror.resize(message.columns, message.rows)
            else -> Unit
        }
    }

    /**
     * The wrapper disconnected. If Claude exited, the session ends; if only the link dropped (e.g. the
     * agent restarts), it stays hook controlled until the wrapper reconnects.
     */
    fun disconnect(wrapperId: String, outgoing: Channel<WrapperMessage>, claudeExited: Boolean) {
        val link = links[wrapperId] ?: return
        if (link.outgoing !== outgoing) return // A newer connection with the same id replaced it.
        // The session changes first, so whoever sees the terminal gone also sees why.
        registry.mutateSession(wrapperId) { current ->
            when {
                current == null -> null
                claudeExited -> current.copy(state = SessionState.ENDED, controlMode = ControlMode.HOOKS, awaiting = null, lastEventAt = clock())
                current.controlMode == ControlMode.WRAPPER -> current.copy(controlMode = ControlMode.HOOKS)
                else -> null
            }
        }
        links.remove(wrapperId)
        link.mirror.close()
        link.outgoing.close()
    }

    override fun isWrapped(sessionId: String): Boolean = linkOf(sessionId) != null

    override fun type(sessionId: String, text: String): Boolean {
        val link = linkOf(sessionId) ?: return false
        val body = if (text.contains('\n') && link.mirror.bracketedPaste) {
            "$PASTE_START$text$PASTE_END"
        } else {
            text.replace(Regex("\\s*\\n\\s*"), " ")
        }
        if (link.outgoing.trySend(WrapperMessage.Input(body)).isFailure) return false
        // Enter as a separate keystroke a moment later: in one write it would count as part of a paste.
        scope.launch {
            delay(submitDelayMs)
            link.outgoing.trySend(WrapperMessage.Input(ENTER))
        }
        return true
    }

    override fun press(sessionId: String, key: TerminalKey): Boolean {
        val link = linkOf(sessionId) ?: return false
        return link.outgoing.trySend(WrapperMessage.Input(key.sequence())).isSuccess
    }

    /**
     * What [sessionId]'s terminal shows, emitted when it changes (checked every [periodMs]). Emits nothing
     * while the session has no wrapper.
     */
    fun screens(sessionId: String, periodMs: Long = SCREEN_PERIOD_MS): Flow<Message.TerminalScreen> = flow {
        var lastMirror: TerminalMirror? = null
        var lastChange = -1L
        while (true) {
            val mirror = linkOf(sessionId)?.mirror
            if (mirror != null && (mirror !== lastMirror || mirror.changeCount != lastChange)) {
                lastMirror = mirror
                lastChange = mirror.changeCount
                emit(Message.TerminalScreen(sessionId, mirror.columns, mirror.snapshot()))
            }
            delay(periodMs)
        }
    }

    private fun linkOf(sessionId: String): Link? = links[sessionId]

    companion object {
        private val WRAPPER_ID = Regex("[A-Za-z0-9-]{8,64}")

        /**
         * The session a hook belongs to: its wrapper's session when Claude was started by `agentmon claude`
         * ([wrapperHeader] set), else Claude's own session id.
         */
        fun sessionIdFor(hookSessionId: String, wrapperHeader: String?): String =
            wrapperHeader?.trim()?.takeIf(::isValidId) ?: hookSessionId

        /** Wrapper ids are UUIDs; anything else is refused so a header cannot inject odd session ids. */
        fun isValidId(wrapperId: String): Boolean = WRAPPER_ID.matches(wrapperId)

        private const val OUTGOING_CAPACITY = 64
        private const val SUBMIT_DELAY_MS = 120L
        private const val SCREEN_PERIOD_MS = 250L
        private const val ENTER = "\r"
        private const val PASTE_START = "\u001B[200~"
        private const val PASTE_END = "\u001B[201~"

        private fun TerminalKey.sequence(): String = when (this) {
            TerminalKey.ENTER -> ENTER
            TerminalKey.ESCAPE -> "\u001B"
            TerminalKey.TAB -> "\t"
            TerminalKey.SHIFT_TAB -> "\u001B[Z"
            TerminalKey.UP -> "\u001B[A"
            TerminalKey.DOWN -> "\u001B[B"
            TerminalKey.CTRL_C -> "\u0003"
            TerminalKey.DIGIT_1 -> "1"
            TerminalKey.DIGIT_2 -> "2"
            TerminalKey.DIGIT_3 -> "3"
        }
    }
}
