package com.claude.codex.ai.monitoring.domain.usecase

import com.claude.codex.ai.monitoring.domain.models.PairingError
import com.claude.codex.ai.monitoring.protocol.PairingCode
import com.claude.codex.ai.monitoring.protocol.PairingOfferDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ParsePairingCodeUseCaseTest {

    private val parse = ParsePairingCodeUseCase()
    private val fingerprint = "a".repeat(64)

    private fun code(url: String, fp: String = fingerprint, token: String = "tok", name: String = "Laptop") =
        PairingCode.encode(PairingOfferDto(url = url, token = token, fp = fp, name = name))

    @Test
    fun `https tunnel code is accepted`() {
        val offer = parse(code("https://agent.appsdev.qzz.io/")).getOrThrow()
        assertEquals("https://agent.appsdev.qzz.io", offer.baseUrl)
        assertEquals(fingerprint, offer.desktopFingerprint)
        assertEquals("Laptop", offer.computerName)
    }

    @Test
    fun `plain http is accepted only for loopback`() {
        assertEquals("http://127.0.0.1:8787", parse(code("http://127.0.0.1:8787")).getOrThrow().baseUrl)
        assertIs<PairingError.InsecureAddress>(parse(code("http://192.168.1.4:8787")).exceptionOrNull())
    }

    @Test
    fun `garbage, wrong fingerprints and other schemes are invalid`() {
        assertIs<PairingError.InvalidCode>(parse("https://example.com").exceptionOrNull())
        assertIs<PairingError.InvalidCode>(parse(code("https://agent.example", fp = "short")).exceptionOrNull())
        assertIs<PairingError.InvalidCode>(parse(code("https://agent.example", token = "")).exceptionOrNull())
        assertIs<PairingError.InvalidCode>(parse(code("ftp://agent.example")).exceptionOrNull())
    }

    @Test
    fun `blank computer name falls back to the host`() {
        assertEquals("agent.example", parse(code("https://agent.example", name = "")).getOrThrow().computerName)
    }
}
