package com.claude.codex.ai.monitoring.desktop.hooks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The JSON Claude Code sends to a hook (code.claude.com/docs/en/hooks, checked against v2.1.283).
 * Every field except the common ones is optional and unknown fields are ignored, so newer or older
 * Claude Code versions still parse.
 */
@Serializable
data class HookEventDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("hook_event_name") val eventName: String,
    val cwd: String,
    @SerialName("transcript_path") val transcriptPath: String? = null,
    @SerialName("permission_mode") val permissionMode: String? = null,
    // UserPromptSubmit
    val prompt: String? = null,
    // PreToolUse / PostToolUse / PostToolUseFailure / PermissionRequest
    @SerialName("tool_name") val toolName: String? = null,
    @SerialName("tool_input") val toolInput: JsonElement? = null,
    // Notification
    @SerialName("notification_type") val notificationType: String? = null,
    val message: String? = null,
    val title: String? = null,
    // PostToolUseFailure (string) and StopFailure (error type)
    val error: String? = null,
    @SerialName("error_details") val errorDetails: String? = null,
    // Stop / StopFailure
    @SerialName("last_assistant_message") val lastAssistantMessage: String? = null,
    // SessionEnd
    val reason: String? = null,
) {
    /** A one-line summary of what a tool call does, e.g. the Bash command or the edited file. */
    fun toolSummary(): String? {
        val input = toolInput as? JsonObject ?: return null
        val key = SUMMARY_KEYS.firstOrNull { input[it] is JsonPrimitive } ?: return null
        return (input[key] as JsonPrimitive).contentOrNull?.lineSequence()?.firstOrNull()?.trim()
    }

    companion object {
        private val SUMMARY_KEYS = listOf("command", "file_path", "path", "pattern", "url", "query", "description", "prompt")

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }
    }
}
