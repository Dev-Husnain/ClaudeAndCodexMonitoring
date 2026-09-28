package com.claude.codex.ai.monitoring.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Versioned wire envelope (spec 8). */
@Serializable
data class Envelope(
    val v: Int = ProtocolConstants.PROTOCOL_VERSION,
    val id: String,
    val type: String,
    val ts: Long,
    val projectId: String? = null,
    val payload: JsonObject = JsonObject(emptyMap()),
)
