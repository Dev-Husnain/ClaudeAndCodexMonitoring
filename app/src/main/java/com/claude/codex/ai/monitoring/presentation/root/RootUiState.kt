package com.claude.codex.ai.monitoring.presentation.root

import com.claude.codex.ai.monitoring.domain.models.ThemeMode

enum class PairState { LOADING, PAIRED, UNPAIRED }

data class RootUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pairState: PairState = PairState.LOADING,
)
