package com.claude.codex.ai.monitoring.core.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

private const val DURATION_MS = 320
private const val OFFSET_DIVISOR = 5

/** Forward: a subtle slide in from the end plus fade (guidelines 5.7). */
fun <S> AnimatedContentTransitionScope<S>.forwardTransition(): ContentTransform =
    (slideInHorizontally(tween(DURATION_MS)) { it / OFFSET_DIVISOR } + fadeIn(tween(DURATION_MS))) togetherWith
        (slideOutHorizontally(tween(DURATION_MS)) { -it / OFFSET_DIVISOR } + fadeOut(tween(DURATION_MS)))

/** Back: the reverse of [forwardTransition]. */
fun <S> AnimatedContentTransitionScope<S>.backTransition(): ContentTransform =
    (slideInHorizontally(tween(DURATION_MS)) { -it / OFFSET_DIVISOR } + fadeIn(tween(DURATION_MS))) togetherWith
        (slideOutHorizontally(tween(DURATION_MS)) { it / OFFSET_DIVISOR } + fadeOut(tween(DURATION_MS)))
