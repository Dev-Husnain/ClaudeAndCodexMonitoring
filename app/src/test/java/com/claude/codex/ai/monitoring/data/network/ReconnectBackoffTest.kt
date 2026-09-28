package com.claude.codex.ai.monitoring.data.network

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReconnectBackoffTest {

    private class FixedRandom(private val value: Double) : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = value
    }

    @Test
    fun `delay doubles per failure without jitter`() {
        val backoff = ReconnectBackoff(baseDelayMs = 1_000, maxDelayMs = 30_000, random = FixedRandom(1.0))
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L), (1..4).map { backoff.delayFor(it) })
    }

    @Test
    fun `delay is capped`() {
        val backoff = ReconnectBackoff(baseDelayMs = 1_000, maxDelayMs = 30_000, random = FixedRandom(1.0))
        assertEquals(30_000L, backoff.delayFor(50))
    }

    @Test
    fun `jitter keeps delay between half and full`() {
        val backoff = ReconnectBackoff(baseDelayMs = 1_000, maxDelayMs = 30_000)
        repeat(100) {
            val delay = backoff.delayFor(3)
            assertTrue(delay in 2_000L..4_000L, "delay $delay out of range")
        }
    }
}
