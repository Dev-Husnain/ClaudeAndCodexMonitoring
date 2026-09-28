package com.claude.codex.ai.monitoring.desktop.security

import com.claude.codex.ai.monitoring.desktop.storage.AppStorage
import com.claude.codex.ai.monitoring.protocol.AgentCrypto
import java.nio.file.Path
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeLines

/** The desktop's own P-256 key pair (spec 6.1). Phones pin its public key when they pair. */
class DesktopIdentity(private val keyPair: KeyPair) {
    val publicKeyBase64: String = AgentCrypto.encodePublicKey(keyPair.public)
    val fingerprint: String = AgentCrypto.fingerprint(publicKeyBase64)

    fun sign(payload: ByteArray): String = AgentCrypto.sign(keyPair.private, payload)

    companion object {
        /** Loads the key from [file], or creates and saves a new one on first run. */
        fun loadOrCreate(file: Path): DesktopIdentity {
            if (file.exists()) {
                val (privateLine, publicLine) = file.readLines()
                val privateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(Base64.decode(privateLine)))
                return DesktopIdentity(KeyPair(AgentCrypto.decodePublicKey(publicLine), privateKey))
            }
            val keyPair = AgentCrypto.generateKeyPair()
            file.writeLines(listOf(Base64.encode(keyPair.private.encoded), AgentCrypto.encodePublicKey(keyPair.public)))
            AppStorage.restrictToOwner(file, directory = false)
            return DesktopIdentity(keyPair)
        }
    }
}
