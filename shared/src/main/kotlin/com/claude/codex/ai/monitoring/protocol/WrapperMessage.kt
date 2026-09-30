package com.claude.codex.ai.monitoring.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Frames between `agentmon claude` and the desktop agent on `ws://127.0.0.1:8787/wrapper`
 * (loopback only, authenticated with the hook secret). Never sent to phones.
 */
@Serializable
sealed interface WrapperMessage {

    // ---- Wrapper -> Desktop ----

    /** First frame. [wrapperId] is also in Claude's environment, so hooks can name their wrapper. */
    @Serializable
    @SerialName("hello")
    data class Hello(val wrapperId: String, val cwd: String, val columns: Int, val rows: Int) : WrapperMessage

    /** Terminal output, decoded as UTF-8. */
    @Serializable
    @SerialName("output")
    data class Output(val data: String) : WrapperMessage

    @Serializable
    @SerialName("resize")
    data class Resize(val columns: Int, val rows: Int) : WrapperMessage

    @Serializable
    @SerialName("exit")
    data class Exit(val code: Int) : WrapperMessage

    // ---- Desktop -> Wrapper ----

    /** Characters to type into Claude's terminal, exactly as given (escape sequences included). */
    @Serializable
    @SerialName("input")
    data class Input(val data: String) : WrapperMessage

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "type"
        }

        fun encode(message: WrapperMessage): String = json.encodeToString(serializer(), message)

        fun decode(text: String): WrapperMessage? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
    }
}
