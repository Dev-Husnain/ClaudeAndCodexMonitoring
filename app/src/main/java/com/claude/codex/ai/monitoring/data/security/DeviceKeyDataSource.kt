package com.claude.codex.ai.monitoring.data.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.annotation.RequiresApi
import com.claude.codex.ai.monitoring.protocol.AgentCrypto
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec

/**
 * This phone's ECDSA P-256 identity key (spec 6.1), held in the Android Keystore. The private key
 * is non-exportable and hardware-backed where available (StrongBox first, then TEE). Blocking;
 * call from an IO dispatcher.
 */
class DeviceKeyDataSource {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    @Synchronized
    fun publicKeyBase64(): String {
        if (!keyStore.containsAlias(ALIAS)) generate()
        return AgentCrypto.encodePublicKey(keyStore.getCertificate(ALIAS).publicKey)
    }

    @Synchronized
    fun sign(payload: ByteArray): String {
        if (!keyStore.containsAlias(ALIAS)) generate()
        val privateKey = keyStore.getKey(ALIAS, null) as PrivateKey
        return AgentCrypto.sign(privateKey, payload)
    }

    @Synchronized
    fun delete() {
        if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)
    }

    private fun generate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && generateInStrongBox()) return
        newGenerator().apply { initialize(specBuilder().build()) }.generateKeyPair()
    }

    /** Returns false when the device has no StrongBox; the caller then uses the TEE. */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun generateInStrongBox(): Boolean = try {
        newGenerator().apply { initialize(specBuilder().setIsStrongBoxBacked(true).build()) }.generateKeyPair()
        true
    } catch (_: StrongBoxUnavailableException) {
        false
    }

    private fun newGenerator() = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)

    private fun specBuilder() = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
        .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
        .setDigests(KeyProperties.DIGEST_SHA256)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "agentmon_device_key"
        const val CURVE = "secp256r1"
    }
}
