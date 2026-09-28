package com.claude.codex.ai.monitoring.core.ui

/** Visual status vocabulary shared by orbs, pills and timeline nodes. */
enum class StatusTone {
    RUNNING,
    WAITING,
    DONE,
    ERROR,
    STALE,
    BRAND,
    ;

    /** How the status orb moves: a soft pulse for live work, a shimmer when attention is needed. */
    val animation: OrbAnimation
        get() = when (this) {
            RUNNING -> OrbAnimation.PULSE
            WAITING -> OrbAnimation.SHIMMER
            DONE, ERROR, STALE, BRAND -> OrbAnimation.NONE
        }
}

enum class OrbAnimation { PULSE, SHIMMER, NONE }
