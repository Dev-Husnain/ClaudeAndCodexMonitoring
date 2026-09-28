package com.claude.codex.ai.monitoring.core.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Emits the current time now and then every [periodMs], so relative times ("5 min ago") stay fresh. */
fun Clock.ticks(periodMs: Long = 30_000L): Flow<Long> = flow {
    while (true) {
        emit(nowMs())
        delay(periodMs)
    }
}
