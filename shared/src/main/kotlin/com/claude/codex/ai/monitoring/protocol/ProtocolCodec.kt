package com.claude.codex.ai.monitoring.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.UUID

/** A decoded frame: the envelope metadata plus its typed message. */
data class Frame(
    val id: String,
    val ts: Long,
    val projectId: String?,
    val message: Message,
)

/**
 * Converts [Message]s to and from envelope JSON. The message's polymorphic `type` discriminator
 * is lifted out of the payload into [Envelope.type].
 */
class ProtocolCodec(
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = TYPE_KEY
    }

    fun encode(message: Message, projectId: String? = null, id: String = newId()): String {
        val tagged = json.encodeToJsonElement(Message.serializer(), message).jsonObject
        val type = (tagged.getValue(TYPE_KEY) as JsonPrimitive).content
        val envelope = Envelope(
            id = id,
            type = type,
            ts = clock(),
            projectId = projectId,
            payload = JsonObject(tagged - TYPE_KEY),
        )
        return json.encodeToString(Envelope.serializer(), envelope)
    }

    /** Returns the decoded frame, or a failure for malformed JSON, unknown types or a newer protocol. */
    fun decode(text: String): Result<Frame> = runCatching {
        val envelope = json.decodeFromString(Envelope.serializer(), text)
        if (envelope.v > ProtocolConstants.PROTOCOL_VERSION) {
            throw SerializationException("Unsupported protocol version ${envelope.v}")
        }
        val tagged = JsonObject(envelope.payload + (TYPE_KEY to JsonPrimitive(envelope.type)))
        Frame(
            id = envelope.id,
            ts = envelope.ts,
            projectId = envelope.projectId,
            message = json.decodeFromJsonElement(Message.serializer(), tagged),
        )
    }

    private companion object {
        const val TYPE_KEY = "type"
    }
}
