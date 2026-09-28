package com.claude.codex.ai.monitoring.data.network

import com.claude.codex.ai.monitoring.protocol.PairRequestDto
import com.claude.codex.ai.monitoring.protocol.PairResponseDto
import com.claude.codex.ai.monitoring.protocol.PairingCode
import com.claude.codex.ai.monitoring.protocol.ProtocolConstants
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

/** `POST /pair`. The call stays open until the owner approves or rejects on the computer. */
class PairingApi(private val client: HttpClient) {

    suspend fun pair(baseUrl: String, request: PairRequestDto): PairResponseDto {
        val json = PairingCode.jsonFormat
        val response = client.post(baseUrl + ProtocolConstants.PATH_PAIR) {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(PairRequestDto.serializer(), request))
            // The desktop waits up to 90 s for the owner; allow a margin on top.
            timeout { requestTimeoutMillis = PAIR_TIMEOUT_MS }
        }
        return json.decodeFromString(PairResponseDto.serializer(), response.bodyAsText())
    }

    private companion object {
        const val PAIR_TIMEOUT_MS = 110_000L
    }
}
