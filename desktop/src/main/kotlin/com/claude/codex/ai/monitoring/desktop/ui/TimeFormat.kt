package com.claude.codex.ai.monitoring.desktop.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clockFormat = DateTimeFormatter.ofPattern("MMM d, HH:mm:ss").withZone(ZoneId.systemDefault())

fun Long.toClockTime(): String = clockFormat.format(Instant.ofEpochMilli(this))

fun Long.toRelative(nowMs: Long): String {
    val seconds = ((nowMs - this) / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> "just now"
        seconds < 3_600 -> "${seconds / 60} min ago"
        seconds < 86_400 -> "${seconds / 3_600} h ago"
        else -> "${seconds / 86_400} d ago"
    }
}
