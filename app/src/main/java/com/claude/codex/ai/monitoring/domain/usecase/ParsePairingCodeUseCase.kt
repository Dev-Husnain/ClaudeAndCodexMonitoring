package com.claude.codex.ai.monitoring.domain.usecase

import com.claude.codex.ai.monitoring.domain.models.PairingError
import com.claude.codex.ai.monitoring.domain.models.PairingOfferModel
import com.claude.codex.ai.monitoring.protocol.PairingCode
import java.net.URI

/**
 * Turns scanned or pasted text into a pairing offer. Plain `http://` is accepted only for
 * loopback (USB pairing via `adb reverse`); anything remote must be `https://`.
 */
class ParsePairingCodeUseCase {
    operator fun invoke(raw: String): Result<PairingOfferModel> {
        val offer = PairingCode.decode(raw) ?: return Result.failure(PairingError.InvalidCode())
        val uri = runCatching { URI(offer.url) }.getOrNull()
        val host = uri?.host
        return when {
            uri == null || host.isNullOrBlank() || offer.token.isBlank() || offer.fp.length != FINGERPRINT_LENGTH ->
                Result.failure(PairingError.InvalidCode())
            uri.scheme == "https" || (uri.scheme == "http" && host in LOOPBACK_HOSTS) -> Result.success(
                PairingOfferModel(
                    baseUrl = offer.url.trimEnd('/'),
                    token = offer.token,
                    desktopFingerprint = offer.fp,
                    computerName = offer.name.ifBlank { host },
                ),
            )
            uri.scheme == "http" -> Result.failure(PairingError.InsecureAddress())
            else -> Result.failure(PairingError.InvalidCode())
        }
    }

    private companion object {
        const val FINGERPRINT_LENGTH = 64
        val LOOPBACK_HOSTS = setOf("127.0.0.1", "localhost")
    }
}
