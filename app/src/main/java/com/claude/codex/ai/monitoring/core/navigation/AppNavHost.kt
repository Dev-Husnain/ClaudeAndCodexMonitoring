package com.claude.codex.ai.monitoring.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.claude.codex.ai.monitoring.presentation.devicessecurity.DevicesSecurityScreen
import com.claude.codex.ai.monitoring.presentation.home.HomeScreen
import com.claude.codex.ai.monitoring.presentation.onboarding.OnboardingScreen
import com.claude.codex.ai.monitoring.presentation.pair.PairScreen
import com.claude.codex.ai.monitoring.presentation.sessiondetail.SessionDetailScreen
import com.claude.codex.ai.monitoring.presentation.settings.SettingsScreen

/**
 * Navigation 3 graph. All back-stack changes happen here; screens only expose callbacks.
 * An unpaired phone always lands on Onboarding. The Pair flow moves to Home itself after its
 * success screen, so losing pairing (unpair, revoke) resets the stack.
 */
@Composable
fun AppNavHost(isPaired: Boolean, modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(if (isPaired) Route.Home else Route.Onboarding)
    val pop: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    LaunchedEffect(isPaired) {
        val inPairingFlow = backStack.lastOrNull() is Route.Onboarding || backStack.lastOrNull() is Route.Pair
        if (!isPaired && !inPairingFlow) backStack.resetTo(Route.Onboarding)
    }

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
            entry<Route.Onboarding> {
                OnboardingScreen(onPairClick = { backStack.add(Route.Pair) })
            }
            entry<Route.Pair> {
                PairScreen(onBack = pop, onPaired = { backStack.resetTo(Route.Home) })
            }
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
                SettingsScreen(onBack = pop, onDevicesClick = { backStack.add(Route.DevicesSecurity) })
            }
            entry<Route.DevicesSecurity> {
                DevicesSecurityScreen(onBack = pop)
            }
        },
    )
}

/** Replaces the whole stack with [route] without ever leaving it empty. */
private fun NavBackStack<NavKey>.resetTo(route: Route) {
    add(route)
    while (size > 1) removeAt(0)
}
