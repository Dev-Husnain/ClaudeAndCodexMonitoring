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

    /**
     * First frame. [wrapperId] is also in Claude's environment, so hooks can name their wrapper. [features] lists
     * what this wrapper supports beyond the first version, e.g. [FEATURE_INPUT_ACK].
     */
    @Serializable
    @SerialName("hello")
    data class Hello(
        val wrapperId: String,
        val cwd: String,
        val columns: Int,
        val rows: Int,
        val features: List<String> = emptyList(),
    ) : WrapperMessage

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

    /** The [Input] with this [id] was written into Claude's terminal ([ok]), or could not be. */
    @Serializable
    @SerialName("input.ack")
    data class InputAck(val id: String, val ok: Boolean) : WrapperMessage

    // ---- Desktop -> Wrapper ----

    /**
     * Characters to type into Claude's terminal, exactly as given (escape sequences included). With an [id], a
     * wrapper that has [FEATURE_INPUT_ACK] answers with [InputAck] once it wrote them.
     */
    @Serializable
    @SerialName("input")
    data class Input(val data: String, val id: String? = null) : WrapperMessage

    companion object {
        /** The wrapper confirms typed input with [InputAck], so the phone only hears "Delivered" when it was. */
        const val FEATURE_INPUT_ACK = "input-ack"

        val json = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "type"
        }

        fun encode(message: WrapperMessage): String = json.encodeToString(serializer(), message)

        fun decode(text: String): WrapperMessage? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
    }
}
