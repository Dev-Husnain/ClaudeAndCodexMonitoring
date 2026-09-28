package com.claude.codex.ai.monitoring.domain.models

/** This phone's pairing with one computer. The desktop key is pinned here (spec 6.2 step 4). */
data class PairingModel(
    /** `https://agent.appsdev.qzz.io` or `http://127.0.0.1:8787`. */
    val baseUrl: String,
    val deviceId: String,
    val deviceName: String,
    val desktopPublicKey: String,
    val desktopFingerprint: String,
    val computerName: String,
    val canSendInput: Boolean,
    val pairedAtMs: Long,
)
