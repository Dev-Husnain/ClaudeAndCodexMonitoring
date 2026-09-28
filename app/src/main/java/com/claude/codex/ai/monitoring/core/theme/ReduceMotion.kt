package com.claude.codex.ai.monitoring.core.theme

import androidx.compose.runtime.staticCompositionLocalOf

/** True when the user disabled system animations (Settings > Accessibility > Remove animations). */
val LocalReduceMotion = staticCompositionLocalOf { false }
