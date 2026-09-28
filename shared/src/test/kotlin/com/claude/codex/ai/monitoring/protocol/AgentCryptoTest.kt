package com.claude.codex.ai.monitoring.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentCryptoTest {

    private val keys = AgentCrypto.generateKeyPair()
    private val other = AgentCrypto.generateKeyPair()

    @Test
    fun `signature verifies only with the matching key and payload`() {
        val payload = AuthPayloads.auth("nonce", "device", "fp")
        val signature = AgentCrypto.sign(keys.private, payload)
        assertTrue(AgentCrypto.verify(keys.public, payload, signature))
        assertFalse(AgentCrypto.verify(other.public, payload, signature))
        assertFalse(AgentCrypto.verify(keys.public, AuthPayloads.auth("other-nonce", "device", "fp"), signature))
    }

    @Test
    fun `a signature for one purpose is useless for another`() {
        val challengeSignature = AgentCrypto.sign(keys.private, AuthPayloads.challenge("n", "d"))
        assertFalse(AgentCrypto.verify(keys.public, AuthPayloads.auth("n", "d", ""), challengeSignature))
    }

    @Test
    fun `malformed signatures and keys never throw`() {
        assertFalse(AgentCrypto.verify(keys.public, byteArrayOf(1), "not base64 !!"))
        assertTrue(runCatching { AgentCrypto.decodePublicKey("AAAA") }.isFailure)
    }

    @Test
    fun `public key round trips and fingerprints are stable`() {
        val encoded = AgentCrypto.encodePublicKey(keys.public)
        assertEquals(keys.public, AgentCrypto.decodePublicKey(encoded))
        assertEquals(64, AgentCrypto.fingerprint(encoded).length)
        assertEquals(AgentCrypto.fingerprint(encoded).take(32), AgentCrypto.deviceIdFor(encoded))
        assertNotEquals(AgentCrypto.fingerprint(encoded), AgentCrypto.fingerprint(AgentCrypto.encodePublicKey(other.public)))
        assertEquals("abcd ef01 2345 6789", AgentCrypto.shortFingerprint("abcdef0123456789ffff"))
    }

    @Test
    fun `tokens are random and url safe`() {
        val a = AgentCrypto.randomToken()
        assertNotEquals(a, AgentCrypto.randomToken())
        assertTrue(a.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertEquals(22, a.length)
    }

    @Test
    fun `pairing code round trips and rejects garbage`() {
        val offer = PairingOfferDto(url = "https://agent.appsdev.qzz.io", token = "t", fp = "f", name = "Laptop")
        assertEquals(offer, PairingCode.decode(PairingCode.encode(offer)))
        assertEquals(offer, PairingCode.decode("  " + PairingCode.encode(offer) + "\n"))
        assertNull(PairingCode.decode("https://example.com"))
        assertNull(PairingCode.decode("AGENTMON1:%%%"))
    }
}
