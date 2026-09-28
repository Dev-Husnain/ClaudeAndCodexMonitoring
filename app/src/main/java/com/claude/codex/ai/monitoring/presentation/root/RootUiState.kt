package com.claude.codex.ai.monitoring.presentation.root

import com.claude.codex.ai.monitoring.domain.models.ThemeMode

data class RootUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)
