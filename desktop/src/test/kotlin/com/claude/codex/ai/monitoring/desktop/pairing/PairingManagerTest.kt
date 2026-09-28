package com.claude.codex.ai.monitoring.desktop.pairing

import com.claude.codex.ai.monitoring.desktop.TestAgent
import com.claude.codex.ai.monitoring.desktop.devices.DeviceGrant
import com.claude.codex.ai.monitoring.protocol.AgentCrypto
import com.claude.codex.ai.monitoring.protocol.AuthPayloads
import com.claude.codex.ai.monitoring.protocol.PairRequestDto
import com.claude.codex.ai.monitoring.protocol.PairStatus
import com.claude.codex.ai.monitoring.protocol.PairingCode
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairingManagerTest {

    private var now = 1_000_000L
    private val agent = TestAgent(clock = { now })
    private val phoneKeys = AgentCrypto.generateKeyPair()
    private val publicKey = AgentCrypto.encodePublicKey(phoneKeys.public)

    private fun requestFor(token: String, proofKeys: java.security.KeyPair = phoneKeys) = PairRequestDto(
        token = token,
        devicePublicKey = publicKey,
        deviceName = "Pixel",
        proof = AgentCrypto.sign(proofKeys.private, AuthPayloads.pair(token, publicKey)),
    )

    private fun token(): String = PairingCode.decode(agent.pairing.createOffer("https://agent.example").code)!!.token

    @Test
    fun `offer encodes the desktop fingerprint`() {
        val offer = PairingCode.decode(agent.pairing.createOffer("https://agent.example").code)!!
        assertEquals(agent.identity.fingerprint, offer.fp)
        assertEquals("https://agent.example", offer.url)
    }

    @Test
    fun `approved request stores the device only after approval`() = runBlocking {
        val grant = DeviceGrant(canSendInput = true, allProjects = false, projectIds = setOf("p1"))
        val result = async { agent.pairing.handle(requestFor(token()), "1.2.3.4") }
        val pending = agent.pairing.pending.filterNotNull().first()
        assertEquals("Pixel", pending.deviceName)
        assertTrue(agent.devices.devices.value.isEmpty())

        agent.pairing.decide(PairingDecision.Approve(grant))
        val response = result.await()
        assertEquals(PairStatus.APPROVED, response.status)
        assertEquals(agent.identity.publicKeyBase64, response.desktopPublicKey)
        assertEquals(true, response.canSendInput)
        val stored = assertNotNull(agent.devices.find(AgentCrypto.deviceIdFor(publicKey)))
        assertEquals(grant, stored.grant)
        assertNull(agent.pairing.pending.value)
    }

    @Test
    fun `rejected request stores nothing`() = runBlocking {
        val result = async { agent.pairing.handle(requestFor(token()), "ip") }
        agent.pairing.pending.filterNotNull().first()
        agent.pairing.decide(PairingDecision.Reject)
        assertEquals(PairStatus.REJECTED, result.await().status)
        assertTrue(agent.devices.devices.value.isEmpty())
    }

    @Test
    fun `token works only once`(): Unit = runBlocking {
        val token = token()
        val first = async { agent.pairing.handle(requestFor(token), "ip") }
        agent.pairing.pending.filterNotNull().first()
        assertEquals(PairStatus.INVALID, agent.pairing.handle(requestFor(token), "ip").status)
        agent.pairing.decide(PairingDecision.Reject)
        assertEquals(PairStatus.REJECTED, first.await().status)
    }

    @Test
    fun `expired token is refused`() = runBlocking {
        val token = token()
        now += 121_000
        assertEquals(PairStatus.EXPIRED, agent.pairing.handle(requestFor(token), "ip").status)
    }

    @Test
    fun `wrong token and forged proof are refused`() = runBlocking {
        token()
        assertEquals(PairStatus.INVALID, agent.pairing.handle(requestFor("guess"), "ip").status)
        val token = token()
        assertEquals(PairStatus.INVALID, agent.pairing.handle(requestFor(token, AgentCrypto.generateKeyPair()), "ip").status)
    }

    @Test
    fun `nobody deciding times out`() = runBlocking {
        assertEquals(PairStatus.TIMEOUT, agent.pairing.handle(requestFor(token()), "ip").status)
        assertTrue(agent.devices.devices.value.isEmpty())
    }
}
