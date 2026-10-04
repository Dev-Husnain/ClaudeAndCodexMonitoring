package com.claude.codex.ai.monitoring.presentation.settings

import com.claude.codex.ai.monitoring.fakes.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val main = StandardTestDispatcher()
    private val repository = FakeSettingsRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `turning background alerts on and off is saved`() {
        val vm = SettingsViewModel(repository, appVersion = "1.0")
        vm.onEvent(SettingsEvent.OnBackgroundAlertsToggle(true))
        main.scheduler.runCurrent()
        assertTrue(repository.settings.value.backgroundAlerts)
        assertTrue(vm.settingsUiState.value.backgroundAlerts)

        vm.onEvent(SettingsEvent.OnBackgroundAlertsToggle(false))
        main.scheduler.runCurrent()
        assertFalse(repository.settings.value.backgroundAlerts)
    }

    @Test
    fun `refused notifications keep alerts off and explain why until allowed`() {
        val vm = SettingsViewModel(repository, appVersion = "1.0")
        vm.onEvent(SettingsEvent.OnNotificationPermissionDenied)
        main.scheduler.runCurrent()
        assertTrue(vm.settingsUiState.value.notificationsBlocked)
        assertFalse(repository.settings.value.backgroundAlerts)

        vm.onEvent(SettingsEvent.OnBackgroundAlertsToggle(true))
        main.scheduler.runCurrent()
        assertFalse(vm.settingsUiState.value.notificationsBlocked)
    }
}
