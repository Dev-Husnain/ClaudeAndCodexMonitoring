package com.claude.codex.ai.monitoring.protocol

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64

/**
 * ECDSA P-256 helpers shared by the phone and the desktop (spec 6.1). Public keys travel as
 * Base64 X.509 (SubjectPublicKeyInfo); a fingerprint is the lowercase hex SHA-256 of those bytes.
 * Uses Kotlin's Base64 rather than java.util.Base64, which is missing below Android API 26.
 */
object AgentCrypto {
    private const val KEY_ALGORITHM = "EC"
    private const val CURVE = "secp256r1"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    private const val DEVICE_ID_HEX_CHARS = 32
    private const val SHORT_FINGERPRINT_CHARS = 16

    private val random = SecureRandom()

    fun generateKeyPair(): KeyPair = KeyPairGenerator.getInstance(KEY_ALGORITHM).apply {
        initialize(ECGenParameterSpec(CURVE), random)
    }.generateKeyPair()

    fun sign(privateKey: PrivateKey, payload: ByteArray): String {
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM).apply {
            initSign(privateKey)
            update(payload)
        }.sign()
        return Base64.encode(signature)
    }

    /** False for a wrong signature and for any malformed input; never throws. */
    fun verify(publicKey: PublicKey, payload: ByteArray, signatureBase64: String): Boolean = runCatching {
        Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initVerify(publicKey)
            update(payload)
            verify(Base64.decode(signatureBase64))
        }
    }.getOrDefault(false)

    fun encodePublicKey(key: PublicKey): String = Base64.encode(key.encoded)

    /** Throws for anything that is not a valid EC public key. */
    fun decodePublicKey(base64: String): PublicKey =
        KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(X509EncodedKeySpec(Base64.decode(base64)))

    fun fingerprint(publicKeyBase64: String): String =
        MessageDigest.getInstance("SHA-256").digest(Base64.decode(publicKeyBase64)).toHex()

    /** A device id is derived from its key, so it cannot be claimed without the matching private key. */
    fun deviceIdFor(publicKeyBase64: String): String = fingerprint(publicKeyBase64).take(DEVICE_ID_HEX_CHARS)

    /** "ab12 cd34 ef56 7890": easy to compare by eye on both screens. */
    fun shortFingerprint(fingerprint: String): String =
        fingerprint.take(SHORT_FINGERPRINT_CHARS).chunked(4).joinToString(" ")

    /** URL-safe random token; 16 bytes = 128 bits (spec 6.2). */
    fun randomToken(bytes: Int = 16): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(ByteArray(bytes).also(random::nextBytes))

    /** Constant-time comparison for secrets. */
    fun secretsEqual(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.encodeToByteArray(), b.encodeToByteArray())

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

/**
 * Exactly what gets signed. Each payload has its own prefix and binds every value that matters,
 * so a signature made for one purpose, device, nonce or desktop can never be reused for another.
 */
object AuthPayloads {
    fun challenge(nonce: String, deviceId: String): ByteArray =
        "agentmon-challenge-v1\n$nonce\n$deviceId".encodeToByteArray()

    fun auth(nonce: String, deviceId: String, desktopFingerprint: String): ByteArray =
        "agentmon-auth-v1\n$nonce\n$deviceId\n$desktopFingerprint".encodeToByteArray()

    fun pair(token: String, devicePublicKey: String): ByteArray =
        "agentmon-pair-v1\n$token\n$devicePublicKey".encodeToByteArray()
}
