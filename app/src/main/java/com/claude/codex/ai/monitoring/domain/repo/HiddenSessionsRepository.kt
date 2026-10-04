package com.claude.codex.ai.monitoring.domain.repo

import kotlinx.coroutines.flow.Flow

/**
 * Sessions the user removed from this phone's lists. Stored only on the phone; nothing on the computer
 * changes. A removed session shows up again if Claude works in it after it was removed.
 */
interface HiddenSessionsRepository {
    /** Session id → when it was removed (epoch ms). */
    val hidden: Flow<Map<String, Long>>

    suspend fun hide(sessionId: String, atMs: Long)
}

/** True when [sessionId] was removed and nothing happened in it since. */
fun Map<String, Long>.hides(sessionId: String, lastActivityMs: Long): Boolean =
    this[sessionId]?.let { removedAt -> lastActivityMs <= removedAt } == true
