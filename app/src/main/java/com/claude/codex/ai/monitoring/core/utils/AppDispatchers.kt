package com.claude.codex.ai.monitoring.core.utils

import kotlinx.coroutines.CoroutineDispatcher

/** Injected so tests can substitute a test dispatcher (guidelines 5.21). */
data class AppDispatchers(
    val io: CoroutineDispatcher,
    val default: CoroutineDispatcher,
)
