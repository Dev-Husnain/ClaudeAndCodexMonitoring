package com.claude.codex.ai.monitoring.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

/** Contents of the pairing QR code (spec 6.2). */
@Serializable
data class PairingOfferDto(
    val v: Int = 1,
    /** Base URL of the agent: `https://agent.appsdev.qzz.io`, or `http://127.0.0.1:8787` over adb reverse. */
    val url: String,
    val token: String,
    /** SHA-256 fingerprint of the desktop public key; the phone pins the key only if it matches. */
    val fp: String,
    val name: String,
)

@Serializable
data class PairRequestDto(
    val token: String,
    val devicePublicKey: String,
    val deviceName: String,
    /** Signature over [AuthPayloads.pair]: proves the phone holds the private key. */
    val proof: String,
)

@Serializable
enum class PairStatus { APPROVED, REJECTED, EXPIRED, INVALID, TIMEOUT, RATE_LIMITED }

@Serializable
data class PairResponseDto(
    val status: PairStatus,
    val deviceId: String? = null,
    val desktopPublicKey: String? = null,
    val computerName: String? = null,
    val canSendInput: Boolean = false,
)

/**
 * Text form of a [PairingOfferDto]: `AGENTMON1:` + base64url(JSON). Encoded in the QR and also
 * shown as a copyable code, so a phone without a usable camera (or an emulator) can still pair.
 */
object PairingCode {
    private const val PREFIX = "AGENTMON1:"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    fun encode(offer: PairingOfferDto): String =
        PREFIX + base64.encode(json.encodeToString(PairingOfferDto.serializer(), offer).encodeToByteArray())

    fun decode(text: String): PairingOfferDto? = runCatching {
        val trimmed = text.trim()
        require(trimmed.startsWith(PREFIX))
        json.decodeFromString(PairingOfferDto.serializer(), base64.decode(trimmed.removePrefix(PREFIX)).decodeToString())
    }.getOrNull()

    val jsonFormat: Json get() = json
}
