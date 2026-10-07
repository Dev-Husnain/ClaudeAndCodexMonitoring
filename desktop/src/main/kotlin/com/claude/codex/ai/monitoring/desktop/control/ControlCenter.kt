package com.claude.codex.ai.monitoring.desktop.control

import com.claude.codex.ai.monitoring.desktop.devices.AuditCategory
import com.claude.codex.ai.monitoring.desktop.devices.AuditLog
import com.claude.codex.ai.monitoring.desktop.history.HeadlessRunner
import com.claude.codex.ai.monitoring.desktop.history.SessionResumer
import com.claude.codex.ai.monitoring.desktop.session.SessionRegistry
import com.claude.codex.ai.monitoring.desktop.wrapper.TypeResult
import com.claude.codex.ai.monitoring.protocol.AvailableProjectDto
import com.claude.codex.ai.monitoring.desktop.wrapper.WrapperInput
import com.claude.codex.ai.monitoring.protocol.AwaitingDto
import com.claude.codex.ai.monitoring.protocol.AwaitingKind
import com.claude.codex.ai.monitoring.protocol.ControlMode
import com.claude.codex.ai.monitoring.protocol.DeliveryResult
import com.claude.codex.ai.monitoring.protocol.EventKind
import com.claude.codex.ai.monitoring.protocol.Message
import com.claude.codex.ai.monitoring.protocol.PastSessionDto
import com.claude.codex.ai.monitoring.protocol.ProtocolConstants
import com.claude.codex.ai.monitoring.protocol.QuickAction
import com.claude.codex.ai.monitoring.protocol.SessionState
import com.claude.codex.ai.monitoring.protocol.TerminalKey
import com.claude.codex.ai.monitoring.protocol.TimelineEventDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/** Result of a phone's input, returned to it in `ack`. */
data class Delivery(val result: DeliveryResult, val detail: String? = null)

/** How a held permission request is answered. */
private sealed interface PermissionAnswer {
    data object Allow : PermissionAnswer
    data class Deny(val interrupt: Boolean) : PermissionAnswer
}

/**
 * Remote control through Claude Code hooks (phase 5a). It works for every hooked session, with no
 * wrapper:
 * - In **Away mode** a `PermissionRequest` hook is held until the phone approves or denies. The reply
 *   is the documented `decision` object; releasing it (Away off, timeout) shows the normal dialog.
 * - In Away mode a `Stop` hook is held until the phone sends the next instruction. The reply is the documented
 *   `{"decision": "block", "reason": …}`, which keeps Claude from stopping and gives it the instruction. (Only
 *   `additionalContext` is a note at the end of the turn: Claude can take it and still stop.) A message sent
 *   while Claude is still working is queued and handed over at the next `Stop`.
 * With Away mode off, every hook is answered at once, so Claude behaves exactly as without AgentMon.
 */
/** What the phone may do with "Projects on this computer". */
interface ProjectAccess {
    fun available(): List<AvailableProjectDto>

    /** Starts watching [projectId] (as "Add project" would); detail = the project id on success. */
    fun addFromPhone(projectId: String, by: String): Delivery
}

class ControlCenter(
    private val registry: SessionRegistry,
    private val audit: AuditLog,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxHoldMs: Long = 50 * 60_000L,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val _awayMode = MutableStateFlow(false)
    val awayMode: StateFlow<Boolean> = _awayMode.asStateFlow()

    // A null completion means "release": answer the hook with no decision.
    private val heldPermissions = HashMap<String, CompletableDeferred<PermissionAnswer?>>()
    private val heldStops = HashMap<String, CompletableDeferred<String?>>()
    private val queued = HashMap<String, String>()

    /** Stop requests from the phone for hook-only sessions, by session, with the time they were made. */
    private val stopRequests = HashMap<String, Long>()

    /** Types into sessions started with `agentmon claude` (phase 5b). */
    @Volatile
    var wrapper: WrapperInput? = null

    /** Projects on this computer for the phone (list and watch); set by the desktop app. */
    var projectAccess: ProjectAccess? = null

    /** Continues saved conversations with `claude -p --resume` (phase 6). */
    @Volatile
    var resumer: SessionResumer? = null

    @Volatile
    var headless: HeadlessRunner? = null

    fun setAwayMode(enabled: Boolean, by: String) {
        if (_awayMode.value == enabled) return
        _awayMode.value = enabled
        registry.broadcast(Message.AwayModeUpdate(enabled))
        audit.record(AuditCategory.ACCESS, "Away mode ${if (enabled) "on" else "off"} ($by)")
        if (!enabled) releaseAll()
    }

    /** Called for every `PermissionRequest` hook. Returns the JSON reply, or null for "no decision". */
    suspend fun onPermissionRequest(sessionId: String, detail: String?): String? {
        // A run the phone started has nobody at the keyboard: its prompts always wait for the phone.
        if (!_awayMode.value && headless?.isRunning(sessionId) != true) return null
        val answer = hold(heldPermissions, sessionId, AwaitingDto(AwaitingKind.PERMISSION, detail, clock()))
        return when (answer) {
            PermissionAnswer.Allow -> permissionJson(allow = true, interrupt = false)
            is PermissionAnswer.Deny -> permissionJson(allow = false, interrupt = answer.interrupt)
            null -> null
        }
    }

    /** Called for every `Stop` hook. Returns the JSON reply that continues Claude, or null to let it stop. */
    suspend fun onStop(sessionId: String, lastMessage: String?): String? {
        synchronized(this) { queued.remove(sessionId) }?.let { return continueJson(it) }
        if (!_awayMode.value) return null
        val text = hold(heldStops, sessionId, AwaitingDto(AwaitingKind.REPLY, lastMessage?.take(ProtocolConstants.MAX_TEXT_CHARS), clock()))
        return text?.let(::continueJson)
    }

    /** A session ended or was removed: nothing may stay held for it. */
    fun release(sessionId: String) {
        synchronized(this) {
            heldPermissions.remove(sessionId)?.complete(null)
            heldStops.remove(sessionId)?.complete(null)
            queued.remove(sessionId)
            stopRequests.remove(sessionId)
        }
    }

    /**
     * Called first for every hook. When the phone asked to stop this session, answers the hook with
     * `{"continue": false}`, which stops Claude entirely (for tool hooks even mid-response). A turn that
     * ends by itself (Stop, StopFailure, SessionEnd) clears the request, so it never hits the next turn.
     * Returns the JSON reply, or null when the hook should be handled as usual.
     */
    fun takeStopRequest(sessionId: String, eventName: String): String? {
        val requestedAt = synchronized(this) {
            val at = stopRequests.remove(sessionId) ?: return null
            if (eventName == "Stop") queued.remove(sessionId) // A stop wins over a queued instruction.
            at
        }
        if (eventName == "StopFailure" || eventName == "SessionEnd" || clock() - requestedAt > STOP_REQUEST_TTL_MS) return null
        registry.mutateSession(sessionId) { current -> current?.copy(state = SessionState.IDLE, awaiting = null, lastEventAt = clock()) }
        registry.addEvent(TimelineEventDto(newId(), sessionId, clock(), EventKind.STOP, "Stopped from your phone", null))
        return buildJsonObject {
            put("continue", false)
            put("stopReason", STOP_REASON)
        }.toString()
    }

    /** An instruction typed on the phone. */
    suspend fun deliverText(sessionId: String, text: String): Delivery {
        val session = registry.session(sessionId) ?: return Delivery(DeliveryResult.FAILED, "Session not found")
        val instruction = text.trim().take(MAX_INSTRUCTION)
        if (instruction.isEmpty()) return Delivery(DeliveryResult.FAILED, "Empty message")
        val resume = resumer
        if (resume != null && !resume.isOpen(session)) {
            // Nobody has this conversation open any more: the message continues it.
            val delivery = resume.resumeEnded(session, instruction)
            if (delivery.result == DeliveryResult.DELIVERED) {
                registry.addEvent(TimelineEventDto(newId(), sessionId, clock(), EventKind.PROMPT, "From your phone", instruction))
            }
            return delivery
        }
        if (session.state == SessionState.ENDED) return Delivery(DeliveryResult.FAILED, "This session has ended")

        val held = synchronized(this) { heldStops.remove(sessionId) }
        val typed = if (held == null && session.controlMode == ControlMode.WRAPPER) wrapper?.typeConfirmed(sessionId, instruction) else null
        val result = when {
            held != null -> {
                held.complete(instruction)
                Delivery(DeliveryResult.DELIVERED)
            }
            typed == TypeResult.CONFIRMED || typed == TypeResult.SENT -> Delivery(DeliveryResult.DELIVERED)
            // The wrapper is connected but did not type it: say so instead of "Delivered".
            typed == TypeResult.NOT_TYPED -> return Delivery(
                DeliveryResult.FAILED,
                "The agentmon terminal on your computer did not take the message. Restart agentmon claude there.",
            )
            else -> {
                synchronized(this) { queued[sessionId] = instruction }
                Delivery(DeliveryResult.QUEUED, "Delivered when Claude finishes its current turn")
            }
        }
        registry.addEvent(TimelineEventDto(newId(), sessionId, clock(), EventKind.PROMPT, "From your phone", instruction))
        return result
    }

    fun pastSessions(projectId: String): List<PastSessionDto>? = resumer?.pastSessions(projectId)

    /** Opens a terminal with Claude on this computer (from the phone); continues [claudeSessionId] when given. */
    fun startTerminal(projectId: String, claudeSessionId: String?, by: String): Delivery {
        val delivery = resumer?.startTerminal(projectId, claudeSessionId)
            ?: return Delivery(DeliveryResult.FAILED, "Starting Claude from the phone is not available here")
        if (delivery.result == DeliveryResult.DELIVERED) {
            audit.record(AuditCategory.ACCESS, "Opened a Claude terminal on this computer ($by)")
        }
        return delivery
    }

    /** Continues a saved conversation (from the phone's history list). */
    fun resume(projectId: String, claudeSessionId: String, text: String): Delivery {
        val instruction = text.trim().take(MAX_INSTRUCTION)
        if (instruction.isEmpty()) return Delivery(DeliveryResult.FAILED, "Empty message")
        val delivery = resumer?.resume(projectId, claudeSessionId, instruction)
            ?: return Delivery(DeliveryResult.FAILED, "Resuming is not available")
        if (delivery.result == DeliveryResult.DELIVERED) {
            val sessionId = delivery.detail ?: claudeSessionId
            registry.addEvent(TimelineEventDto(newId(), sessionId, clock(), EventKind.PROMPT, "From your phone", instruction))
        }
        return delivery
    }

    /** Approve / Deny / Interrupt / Continue from the phone. */
    fun quickAction(sessionId: String, action: QuickAction): Delivery {
        val permission = synchronized(this) { heldPermissions.remove(sessionId) }
        if (permission != null) {
            val answer = when (action) {
                QuickAction.APPROVE -> PermissionAnswer.Allow
                QuickAction.DENY -> PermissionAnswer.Deny(interrupt = false)
                QuickAction.INTERRUPT -> PermissionAnswer.Deny(interrupt = true)
                QuickAction.CONTINUE -> {
                    synchronized(this) { heldPermissions[sessionId] = permission }
                    return Delivery(DeliveryResult.FAILED, "Claude is asking for permission: approve or deny")
                }
            }
            permission.complete(answer)
            val title = when (action) {
                QuickAction.APPROVE -> "Approved from your phone"
                QuickAction.INTERRUPT -> "Stopped from your phone"
                else -> "Denied from your phone"
            }
            registry.addEvent(TimelineEventDto(newId(), sessionId, clock(), EventKind.NOTIFICATION, title, null))
            return Delivery(DeliveryResult.DELIVERED)
        }
        val stop = synchronized(this) { heldStops.remove(sessionId) }
        if (stop != null) {
            return when (action) {
                QuickAction.CONTINUE -> {
                    stop.complete(CONTINUE_TEXT)
                    registry.addEvent(TimelineEventDto(newId(), sessionId, clock(), EventKind.PROMPT, "From your phone", CONTINUE_TEXT))
                    Delivery(DeliveryResult.DELIVERED)
                }
                else -> {
                    stop.complete(null) // Let Claude stop.
                    Delivery(DeliveryResult.DELIVERED, "Claude stops here")
                }
            }
        }
        if (action == QuickAction.INTERRUPT && headless?.stop(sessionId) == true) {
            registry.addEvent(TimelineEventDto(newId(), sessionId, clock(), EventKind.STOP, "Stopped from your phone", null))
            return Delivery(DeliveryResult.DELIVERED)
        }
        wrapperQuickAction(sessionId, action)?.let { return it }
        if (action == QuickAction.INTERRUPT) return requestStop(sessionId)
        return Delivery(DeliveryResult.FAILED, "Claude is not waiting for you right now")
    }

    /** Stop for a session without a terminal: applied by the next hook (see [takeStopRequest]). */
    private fun requestStop(sessionId: String): Delivery {
        val session = registry.session(sessionId) ?: return Delivery(DeliveryResult.FAILED, "Session not found")
        if (session.state !in STOPPABLE) return Delivery(DeliveryResult.FAILED, "Claude is not working right now")
        synchronized(this) { stopRequests[sessionId] = clock() }
        registry.addEvent(TimelineEventDto(newId(), sessionId, clock(), EventKind.NOTIFICATION, "Stop requested from your phone", null))
        return Delivery(DeliveryResult.QUEUED, "Claude stops at its next step")
    }

    /** A key pressed on the phone's terminal keys row (wrapper sessions only). */
    fun pressKey(sessionId: String, key: TerminalKey): Delivery {
        val input = wrapper?.takeIf { it.isWrapped(sessionId) } ?: return Delivery(DeliveryResult.FAILED, "This session has no terminal")
        return if (input.press(sessionId, key)) Delivery(DeliveryResult.DELIVERED) else Delivery(DeliveryResult.FAILED, "The terminal did not accept the key")
    }

    /**
     * Without a held hook, a wrapper session is answered by pressing keys in Claude's own dialog
     * ([ClaudeCodePromptProfile]). Only while Claude waits for input, so Enter never submits a draft.
     */
    private fun wrapperQuickAction(sessionId: String, action: QuickAction): Delivery? {
        val input = wrapper?.takeIf { it.isWrapped(sessionId) } ?: return null
        val session = registry.session(sessionId) ?: return null
        val sent = when (action) {
            QuickAction.CONTINUE -> if (session.state == SessionState.RUNNING) return null else input.type(sessionId, CONTINUE_TEXT)
            QuickAction.INTERRUPT -> input.press(sessionId, ClaudeCodePromptProfile.INTERRUPT)
            QuickAction.APPROVE, QuickAction.DENY -> {
                if (session.state != SessionState.WAITING_INPUT) return null
                input.press(sessionId, if (action == QuickAction.APPROVE) ClaudeCodePromptProfile.APPROVE else ClaudeCodePromptProfile.DENY)
            }
        }
        if (!sent) return Delivery(DeliveryResult.FAILED, "The terminal did not accept the key")
        if (action == QuickAction.INTERRUPT) {
            // Esc ends the turn without any hook (Stop does not fire on an interrupt), so record it here.
            registry.mutateSession(sessionId) { current ->
                current?.takeIf { it.state in STOPPABLE }?.copy(state = SessionState.IDLE, lastEventAt = clock())
            }
        }
        val title = when (action) {
            QuickAction.APPROVE -> "Approved from your phone"
            QuickAction.DENY -> "Denied from your phone"
            QuickAction.INTERRUPT -> "Stopped from your phone"
            QuickAction.CONTINUE -> "From your phone"
        }
        registry.addEvent(TimelineEventDto(newId(), sessionId, clock(), if (action == QuickAction.CONTINUE) EventKind.PROMPT else EventKind.NOTIFICATION, title, if (action == QuickAction.CONTINUE) CONTINUE_TEXT else null))
        return Delivery(DeliveryResult.DELIVERED)
    }

    private suspend fun <T : Any> hold(held: HashMap<String, CompletableDeferred<T?>>, sessionId: String, awaiting: AwaitingDto): T? {
        val deferred = CompletableDeferred<T?>()
        // A newer request for the same session replaces an older one, which is released without a decision.
        synchronized(this) { held.put(sessionId, deferred)?.complete(null) }
        registry.updateSession(sessionId) { it.copy(awaiting = awaiting) }
        return try {
            withTimeoutOrNull(maxHoldMs) { deferred.await() }
        } finally {
            synchronized(this) { if (held[sessionId] === deferred) held.remove(sessionId) }
            // Only clear our own marker: a newer hold for this session may already have set its own.
            registry.mutateSession(sessionId) { current -> current?.takeIf { it.awaiting === awaiting }?.copy(awaiting = null) }
        }
    }

    private fun releaseAll() {
        synchronized(this) {
            heldPermissions.values.forEach { it.complete(null) }
            heldStops.values.forEach { it.complete(null) }
            heldPermissions.clear()
            heldStops.clear()
        }
    }

    private fun permissionJson(allow: Boolean, interrupt: Boolean): String = buildJsonObject {
        put(
            "hookSpecificOutput",
            buildJsonObject {
                put("hookEventName", "PermissionRequest")
                put(
                    "decision",
                    buildJsonObject {
                        put("behavior", if (allow) "allow" else "deny")
                        if (!allow) {
                            put("message", "The user denied this from their phone (AgentMon).")
                            put("interrupt", interrupt)
                        }
                    },
                )
            },
        )
    }.toString()

    /** Keeps Claude from stopping and hands it the instruction (Stop decision control: `block` + `reason`). */
    private fun continueJson(instruction: String): String = buildJsonObject {
        put("decision", "block")
        put("reason", "The user sent this from their phone (AgentMon). Continue with it:\n$instruction")
    }.toString()

    private companion object {
        const val MAX_INSTRUCTION = 4_000
        const val CONTINUE_TEXT = "Continue."
        const val STOP_REASON = "Stopped from the phone (AgentMon)."
        const val STOP_REQUEST_TTL_MS = 30 * 60_000L
        val STOPPABLE = setOf(SessionState.RUNNING, SessionState.WAITING_INPUT, SessionState.STALE)
    }
}
