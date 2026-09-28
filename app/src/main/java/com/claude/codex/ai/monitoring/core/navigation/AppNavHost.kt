package com.claude.codex.ai.monitoring.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.claude.codex.ai.monitoring.presentation.home.HomeScreen
import com.claude.codex.ai.monitoring.presentation.sessiondetail.SessionDetailScreen
import com.claude.codex.ai.monitoring.presentation.settings.SettingsScreen

/** Navigation 3 graph. All back-stack changes happen here; screens only expose callbacks. */
@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(Route.Home)
    val pop: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = pop,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = { forwardTransition() },
        popTransitionSpec = { backTransition() },
        predictivePopTransitionSpec = { backTransition() },
        entryProvider = entryProvider {
            entry<Route.Home> {
                HomeScreen(
                    onSessionClick = { sessionId -> backStack.add(Route.SessionDetail(sessionId)) },
                    onSettingsClick = { backStack.add(Route.Settings) },
                )
            }
            entry<Route.SessionDetail> { route ->
                SessionDetailScreen(sessionId = route.sessionId, onBack = pop)
            }
            entry<Route.Settings> {
                SettingsScreen(onBack = pop)
            }
        },
    )
}
