package com.claude.codex.ai.monitoring.desktop.control

import com.claude.codex.ai.monitoring.desktop.devices.AuditCategory
import com.claude.codex.ai.monitoring.desktop.devices.AuditLog
import com.claude.codex.ai.monitoring.desktop.session.SessionRegistry
import com.claude.codex.ai.monitoring.protocol.AwaitingDto
import com.claude.codex.ai.monitoring.protocol.AwaitingKind
import com.claude.codex.ai.monitoring.protocol.ControlMode
import com.claude.codex.ai.monitoring.protocol.DeliveryResult
import com.claude.codex.ai.monitoring.protocol.EventKind
import com.claude.codex.ai.monitoring.protocol.Message
import com.claude.codex.ai.monitoring.protocol.ProtocolConstants
import com.claude.codex.ai.monitoring.protocol.QuickAction
import com.claude.codex.ai.monitoring.protocol.SessionState
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
 * - In Away mode a `Stop` hook is held until the phone sends the next instruction, which reaches Claude
 *   as `additionalContext` and continues the conversation. A message sent while Claude is still
 *   working is queued and handed over at the next `Stop`.
 * With Away mode off, every hook is answered at once, so Claude behaves exactly as without AgentMon.
 */
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

    /** Set by the wrapper bridge (phase 5b): writes text into a wrapper-started session's terminal. */
    @Volatile
    var wrapperInput: ((sessionId: String, text: String) -> Boolean)? = null

    fun setAwayMode(enabled: Boolean, by: String) {
        if (_awayMode.value == enabled) return
        _awayMode.value = enabled
        registry.broadcast(Message.AwayModeUpdate(enabled))
        audit.record(AuditCategory.ACCESS, "Away mode ${if (enabled) "on" else "off"} ($by)")
        if (!enabled) releaseAll()
    }

    /** Called for every `PermissionRequest` hook. Returns the JSON reply, or null for "no decision". */
    suspend fun onPermissionRequest(sessionId: String, detail: String?): String? {
        if (!_awayMode.value) return null
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
        }
    }

    /** An instruction typed on the phone. */
    fun deliverText(sessionId: String, text: String): Delivery {
        val session = registry.session(sessionId) ?: return Delivery(DeliveryResult.FAILED, "Session not found")
        if (session.state == SessionState.ENDED) return Delivery(DeliveryResult.FAILED, "This session has ended")
        val instruction = text.trim().take(MAX_INSTRUCTION)
        if (instruction.isEmpty()) return Delivery(DeliveryResult.FAILED, "Empty message")

        val held = synchronized(this) { heldStops.remove(sessionId) }
        val result = when {
            held != null -> {
                held.complete(instruction)
                Delivery(DeliveryResult.DELIVERED)
            }
            session.controlMode == ControlMode.WRAPPER && wrapperInput?.invoke(sessionId, instruction) == true ->
                Delivery(DeliveryResult.DELIVERED)
            else -> {
                synchronized(this) { queued[sessionId] = instruction }
                Delivery(DeliveryResult.QUEUED, "Delivered when Claude finishes its current turn")
            }
        }
        registry.addEvent(TimelineEventDto(newId(), sessionId, clock(), EventKind.PROMPT, "From your phone", instruction))
        return result
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
        return Delivery(DeliveryResult.FAILED, "Claude is not waiting for you right now")
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

    private fun continueJson(instruction: String): String = buildJsonObject {
        put(
            "hookSpecificOutput",
            buildJsonObject {
                put("hookEventName", "Stop")
                put("additionalContext", "The user sent this from their phone (AgentMon). Continue with it:\n$instruction")
            },
        )
    }.toString()

    private companion object {
        const val MAX_INSTRUCTION = 4_000
        const val CONTINUE_TEXT = "Continue."
    }
}
