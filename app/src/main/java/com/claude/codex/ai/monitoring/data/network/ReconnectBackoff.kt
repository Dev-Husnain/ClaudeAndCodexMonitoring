package com.claude.codex.ai.monitoring.data.network

import kotlin.math.min
import kotlin.random.Random

/** Exponential back-off with jitter (spec 8): 1s, 2s, 4s … capped at 30s, each randomised to 50–100%. */
class ReconnectBackoff(
    private val baseDelayMs: Long = 1_000,
    private val maxDelayMs: Long = 30_000,
    private val random: Random = Random.Default,
) {
    fun delayFor(failures: Int): Long {
        val exponent = (failures - 1).coerceIn(0, MAX_EXPONENT)
        val capped = min(maxDelayMs, baseDelayMs shl exponent)
        val jitter = 0.5 + random.nextDouble() * 0.5
        return (capped * jitter).toLong()
    }

    private companion object {
        const val MAX_EXPONENT = 20
    }
}
