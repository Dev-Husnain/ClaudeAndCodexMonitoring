package com.claude.codex.ai.monitoring.desktop.session

import com.claude.codex.ai.monitoring.desktop.hooks.HookEventDto
import com.claude.codex.ai.monitoring.protocol.ControlMode
import com.claude.codex.ai.monitoring.protocol.EventKind
import com.claude.codex.ai.monitoring.protocol.SessionDto
import com.claude.codex.ai.monitoring.protocol.SessionState
import com.claude.codex.ai.monitoring.protocol.TimelineEventDto
import java.util.UUID

/** What one hook event does to a session: its new state and, optionally, a timeline entry. */
data class Transition(val session: SessionDto, val event: TimelineEventDto?)

/**
 * Session state machine (spec 7.3), driven by Claude Code hook events. Pure: time and ids are
 * passed in, so every transition is unit tested.
 *
 * - UserPromptSubmit / PreToolUse / PostToolUse(Failure) → RUNNING
 * - PermissionRequest, Notification(permission_prompt / elicitation / agent_needs_input) → WAITING_INPUT
 * - Stop → IDLE; StopFailure → ERROR; SessionEnd → ENDED, except in an `agentmon claude` terminal (WRAPPER):
 *   there `/clear` and `/resume` end one conversation and start the next in the same terminal, so the session
 *   stays (IDLE, "New conversation"); the wrapper itself reports when Claude really exits.
 * - RUNNING with no event for [staleAfterMs] → STALE (see [markStale])
 */
object SessionStateMachine {

    fun apply(
        current: SessionDto?,
        hook: HookEventDto,
        projectId: String,
        nowMs: Long,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): Transition? {
        val base = current ?: SessionDto(
            sessionId = hook.sessionId,
            projectId = projectId,
            state = SessionState.RUNNING,
            // Every hooked session can be driven from the phone in Away mode (phase 5).
            controlMode = ControlMode.HOOKS,
            startedAt = nowMs,
            lastEventAt = nowMs,
        )
        fun event(kind: EventKind, title: String, detail: String?) =
            TimelineEventDto(newId(), hook.sessionId, nowMs, kind, title, detail?.takeIf { it.isNotBlank() })

        return when (hook.eventName) {
            "UserPromptSubmit" -> Transition(
                base.copy(state = SessionState.RUNNING, lastEventAt = nowMs, lastMessageSnippet = hook.prompt?.oneLine(), errorInfo = null),
                event(EventKind.PROMPT, "Prompt", hook.prompt),
            )
            "PreToolUse" -> Transition(
                base.copy(state = SessionState.RUNNING, lastEventAt = nowMs, lastTool = hook.toolName ?: base.lastTool, errorInfo = null),
                event(EventKind.TOOL_USE, hook.toolName ?: "Tool", hook.toolSummary()),
            )
            // Completion of a tool: state only (an entry per tool call is already added by PreToolUse).
            "PostToolUse" -> Transition(base.copy(state = SessionState.RUNNING, lastEventAt = nowMs), null)
            "PostToolUseFailure" -> Transition(
                base.copy(state = SessionState.RUNNING, lastEventAt = nowMs),
                event(EventKind.TOOL_RESULT, "${hook.toolName ?: "Tool"} failed", hook.error?.oneLine()),
            )
            "PermissionRequest" -> waiting(base, hook, nowMs, event(EventKind.NOTIFICATION, "Permission needed", permissionText(hook)))
            "Notification" -> when (hook.notificationType) {
                "permission_prompt", "elicitation_dialog", "elicitation_url_dialog", "agent_needs_input" ->
                    // PermissionRequest usually got there first; do not log the same prompt twice.
                    if (base.state == SessionState.WAITING_INPUT && current != null) {
                        Transition(base.copy(lastEventAt = nowMs), null)
                    } else {
                        waiting(base, hook, nowMs, event(EventKind.NOTIFICATION, hook.title ?: "Needs your input", hook.message))
                    }
                "idle_prompt" -> Transition(base.copy(state = SessionState.IDLE), null)
                else -> null
            }
            "Stop" -> Transition(
                base.copy(state = SessionState.IDLE, lastEventAt = nowMs, lastMessageSnippet = hook.lastAssistantMessage?.trim()?.ifEmpty { null } ?: base.lastMessageSnippet),
                event(EventKind.STOP, "Finished", hook.lastAssistantMessage),
            )
            "StopFailure" -> {
                val info = listOfNotNull(hook.error, hook.errorDetails).joinToString(": ").ifBlank { "API error" }
                Transition(
                    base.copy(state = SessionState.ERROR, lastEventAt = nowMs, errorInfo = info),
                    event(EventKind.ERROR, "Stopped by an error", hook.lastAssistantMessage ?: info),
                )
            }
            "SessionEnd" -> if (base.controlMode == ControlMode.WRAPPER) {
                val switched = hook.reason == "clear" || hook.reason == "resume"
                Transition(
                    base.copy(state = SessionState.IDLE, awaiting = null, lastEventAt = nowMs),
                    event(EventKind.SESSION_END, if (switched) "New conversation" else "Session ended", endReason(hook.reason)),
                )
            } else {
                Transition(
                    base.copy(state = SessionState.ENDED, lastEventAt = nowMs),
                    event(EventKind.SESSION_END, "Session ended", endReason(hook.reason)),
                )
            }
            else -> null
        }
    }

    /** RUNNING sessions silent for [staleAfterMs] become STALE ("possibly stuck"). */
    fun markStale(session: SessionDto, nowMs: Long, staleAfterMs: Long): SessionDto? =
        session.takeIf { it.state == SessionState.RUNNING && nowMs - it.lastEventAt >= staleAfterMs }
            ?.copy(state = SessionState.STALE)

    private fun waiting(base: SessionDto, hook: HookEventDto, nowMs: Long, event: TimelineEventDto) = Transition(
        base.copy(state = SessionState.WAITING_INPUT, lastEventAt = nowMs, lastMessageSnippet = event.detail ?: hook.message, lastTool = hook.toolName ?: base.lastTool),
        event,
    )

    private fun permissionText(hook: HookEventDto): String {
        val summary = hook.toolSummary()
        return listOfNotNull(hook.toolName, summary).joinToString(": ").ifBlank { "Claude needs your permission" }
    }

    /** Claude Code's SessionEnd `reason` in words; "other" says nothing useful, so it is dropped. */
    private fun endReason(reason: String?): String? = when (reason) {
        "clear" -> "Cleared with /clear"
        "resume" -> "Switched to another conversation"
        "logout" -> "Logged out"
        "prompt_input_exit" -> "Exited Claude Code"
        else -> null
    }

    private fun String.oneLine(): String = lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
}
