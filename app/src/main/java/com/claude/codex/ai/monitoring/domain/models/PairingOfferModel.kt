package com.claude.codex.ai.monitoring.domain.models

/** A validated pairing QR code, before the request is sent. */
data class PairingOfferModel(
    val baseUrl: String,
    val token: String,
    val desktopFingerprint: String,
    val computerName: String,
)
