package com.claude.codex.ai.monitoring.desktop.hooks

import com.claude.codex.ai.monitoring.desktop.devices.AuditCategory
import com.claude.codex.ai.monitoring.desktop.devices.AuditLog
import com.claude.codex.ai.monitoring.desktop.session.SessionTracker
import com.claude.codex.ai.monitoring.protocol.AgentCrypto

/** Outcome of one POST /hook, mapped to an HTTP status by the route. */
enum class HookResult { ACCEPTED, IGNORED, FORBIDDEN, BAD_REQUEST }

/**
 * Validates and applies Claude Code hook posts (spec 6.4: loopback only plus a shared secret).
 * The Cloudflare tunnel also delivers requests to 127.0.0.1, so requests that carry Cloudflare
 * headers are refused outright: hooks must come from this computer.
 */
class HookReceiver(
    private val secret: String,
    private val tracker: SessionTracker,
    private val audit: AuditLog,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var lastRejectionAuditMs = 0L

    fun receive(secretHeader: String?, viaTunnel: Boolean, body: String): HookResult {
        if (viaTunnel || secretHeader == null || !AgentCrypto.secretsEqual(secretHeader, secret)) {
            auditRejection(if (viaTunnel) "Hook call through the tunnel refused" else "Hook call with a wrong secret refused")
            return HookResult.FORBIDDEN
        }
        val event = runCatching { HookEventDto.json.decodeFromString(HookEventDto.serializer(), body) }.getOrNull()
            ?: return HookResult.BAD_REQUEST
        return if (tracker.onHook(event)) HookResult.ACCEPTED else HookResult.IGNORED
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
