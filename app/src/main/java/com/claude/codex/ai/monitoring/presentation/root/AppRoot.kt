package com.claude.codex.ai.monitoring.presentation.root

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claude.codex.ai.monitoring.core.navigation.AppNavHost
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.ui.AuroraBackground
import com.claude.codex.ai.monitoring.domain.models.ThemeMode
import org.koin.compose.viewmodel.koinViewModel

/**
 * Applies the user's theme choice and hosts the navigation graph once the pairing state is known.
 * No padding or insets here: every screen owns its own (guidelines 5.8).
 */
@Composable
fun AppRoot(
    modifier: Modifier = Modifier,
    viewModel: RootViewModel = koinViewModel(),
) {
    val state by viewModel.rootUiState.collectAsStateWithLifecycle()
    val darkTheme = when (state.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    AppTheme(darkTheme = darkTheme) {
        Box(modifier = modifier.fillMaxSize()) {
            if (state.pairState == PairState.LOADING) {
                // A few milliseconds while DataStore loads: the themed background, no flash of the wrong screen.
                AuroraBackground(modifier = Modifier.fillMaxSize()) { }
            } else {
                // One call site, so the back stack survives pairing changes; AppNavHost reacts to them itself.
                AppNavHost(isPaired = state.pairState == PairState.PAIRED)
            }
        }
    }
}
