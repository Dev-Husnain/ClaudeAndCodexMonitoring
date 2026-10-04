package com.claude.codex.ai.monitoring.desktop.hooks

import com.claude.codex.ai.monitoring.desktop.control.ControlCenter
import com.claude.codex.ai.monitoring.desktop.devices.AuditCategory
import com.claude.codex.ai.monitoring.desktop.devices.AuditLog
import com.claude.codex.ai.monitoring.desktop.session.SessionTracker
import com.claude.codex.ai.monitoring.desktop.wrapper.WrapperHub
import com.claude.codex.ai.monitoring.protocol.AgentCrypto

/** Outcome of one POST /hook, mapped to an HTTP status by the route. */
enum class HookResult { ACCEPTED, IGNORED, FORBIDDEN, BAD_REQUEST }

/** [body] is JSON for Claude Code (a decision), or null for "no decision" (empty 204). */
data class HookResponse(val result: HookResult, val body: String? = null)

/**
 * Validates and applies Claude Code hook posts (spec 6.4: loopback only plus a shared secret).
 * The Cloudflare tunnel also delivers requests to 127.0.0.1, so requests that carry Cloudflare
 * headers are refused outright: hooks must come from this computer. `PermissionRequest` and `Stop`
 * may be held by the [ControlCenter] while Away mode waits for the phone.
 */
class HookReceiver(
    private val secret: String,
    private val tracker: SessionTracker,
    private val audit: AuditLog,
    private val control: ControlCenter? = null,
    /** The session a hook of this Claude session id belongs to, when another one shows it (resumes). */
    private val sessionAlias: (claudeSessionId: String) -> String? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var lastRejectionAuditMs = 0L

    /** [wrapperHeader] is the wrapper id when Claude was started by `agentmon claude`, else blank. */
    suspend fun receive(secretHeader: String?, viaTunnel: Boolean, body: String, wrapperHeader: String? = null): HookResponse {
        if (viaTunnel || secretHeader == null || !AgentCrypto.secretsEqual(secretHeader, secret)) {
            auditRejection(if (viaTunnel) "Hook call through the tunnel refused" else "Hook call with a wrong secret refused")
            return HookResponse(HookResult.FORBIDDEN)
        }
        val decoded = runCatching { HookEventDto.json.decodeFromString(HookEventDto.serializer(), body) }.getOrNull()
            ?: return HookResponse(HookResult.BAD_REQUEST)
        // Claude started by `agentmon claude` is filed under its terminal's session, a resumed conversation
        // under the session the phone is looking at.
        val sessionId = WrapperHub.sessionIdFor(decoded.sessionId, wrapperHeader).let { sessionAlias(it) ?: it }
        val event = decoded.copy(sessionId = sessionId)
        if (!tracker.onHook(event)) return HookResponse(HookResult.IGNORED)
        if (sessionId != decoded.sessionId) tracker.recordClaudeSessionId(sessionId, decoded.sessionId)
        control?.takeStopRequest(event.sessionId, event.eventName)?.let { return HookResponse(HookResult.ACCEPTED, it) }

        val reply = when (event.eventName) {
            "PermissionRequest" -> control?.onPermissionRequest(
                event.sessionId,
                listOfNotNull(event.toolName, event.toolSummary()).joinToString(": ").ifBlank { null },
            )
            "Stop" -> control?.onStop(event.sessionId, event.lastAssistantMessage)
            "SessionEnd" -> {
                control?.release(event.sessionId)
                null
            }
            else -> null
        }
        return HookResponse(HookResult.ACCEPTED, reply)
    }

    /** At most one audit entry per minute, so a misbehaving caller cannot flood the log. */
    @Synchronized
    private fun auditRejection(message: String) {
        val now = clock()
        if (now - lastRejectionAuditMs >= AUDIT_INTERVAL_MS) {
            lastRejectionAuditMs = now
            audit.record(AuditCategory.SERVER, message)
        }
    }

    private companion object {
        const val AUDIT_INTERVAL_MS = 60_000L
    }
}
