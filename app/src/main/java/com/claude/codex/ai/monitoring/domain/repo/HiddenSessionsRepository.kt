package com.claude.codex.ai.monitoring.domain.repo

import kotlinx.coroutines.flow.Flow

/**
 * What the user removed from this phone's lists: sessions, their saved conversations (by Claude's id) and
 * projects left empty ([projectKey]). Stored only on the phone; nothing on the computer changes. A removed
 * session shows up again if Claude works in it after it was removed.
 */
interface HiddenSessionsRepository {
    /** Session id → when it was removed (epoch ms). */
    val hidden: Flow<Map<String, Long>>

    /** Hides all [ids] at once, so a session and everything that belongs to it disappear together. */
    suspend fun hide(ids: Collection<String>, atMs: Long)

    suspend fun hide(id: String, atMs: Long) = hide(listOf(id), atMs)
}

/** The key under which a project is hidden, kept apart from session ids. */
fun projectKey(projectId: String) = "project:$projectId"

/**
 * Claude saves a conversation's transcript a moment after the last event the phone saw, so a removed
 * conversation is matched against History with this much slack.
 */
const val TRANSCRIPT_SLACK_MS = 5 * 60_000L

/** True when [sessionId] was removed and nothing happened in it since. */
fun Map<String, Long>.hides(sessionId: String, lastActivityMs: Long, slackMs: Long = 0L): Boolean =
    this[sessionId]?.let { removedAt -> lastActivityMs <= removedAt + slackMs } == true
