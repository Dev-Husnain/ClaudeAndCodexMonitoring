package com.claude.codex.ai.monitoring.desktop.security

/**
 * Sliding-window failure counter keyed by IP or device id (spec 6.3). Once a key has
 * [maxFailures] failures inside [windowMs] it is blocked until old failures age out.
 */
class RateLimiter(
    private val maxFailures: Int = 5,
    private val windowMs: Long = 5 * 60_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val failures = HashMap<String, ArrayDeque<Long>>()

    @Synchronized
    fun isLimited(key: String): Boolean = prune(key).size >= maxFailures

    @Synchronized
    fun recordFailure(key: String) {
        prune(key).addLast(clock())
    }

    @Synchronized
    fun reset(key: String) {
        failures.remove(key)
    }

    private fun prune(key: String): ArrayDeque<Long> {
        val entries = failures.getOrPut(key) { ArrayDeque() }
        val cutoff = clock() - windowMs
        while (entries.isNotEmpty() && entries.first() < cutoff) entries.removeFirst()
        return entries
    }
}
