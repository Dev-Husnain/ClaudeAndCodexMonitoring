package com.claude.codex.ai.monitoring.core.utils

import com.claude.codex.ai.monitoring.R

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS

/** "just now", "5 min ago", "3 h ago", "2 days ago". Future timestamps (clock skew) read as "just now". */
fun Long.toRelativeTime(nowMs: Long): UiText {
    val elapsed = (nowMs - this).coerceAtLeast(0)
    return when {
        elapsed < MINUTE_MS -> UiText.Res(R.string.time_just_now)
        elapsed < HOUR_MS -> UiText.Plural(R.plurals.time_minutes_ago, (elapsed / MINUTE_MS).toInt())
        elapsed < DAY_MS -> UiText.Plural(R.plurals.time_hours_ago, (elapsed / HOUR_MS).toInt())
        else -> UiText.Plural(R.plurals.time_days_ago, (elapsed / DAY_MS).toInt())
    }
}
