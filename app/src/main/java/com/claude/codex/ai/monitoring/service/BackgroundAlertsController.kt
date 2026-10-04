package com.claude.codex.ai.monitoring.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.claude.codex.ai.monitoring.domain.repo.PairingRepository
import com.claude.codex.ai.monitoring.domain.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Runs [AgentMonitorService] while background alerts are on and the phone is paired. Android only lets
 * an app start a foreground service while it is visible, so the service is (re)started whenever the
 * app comes to the front, and keeps running after it goes to the background.
 */
class BackgroundAlertsController(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val pairingRepository: PairingRepository,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch(Dispatchers.Main) {
            val visible = ProcessLifecycleOwner.get().lifecycle.currentStateFlow
                .map { it.isAtLeast(Lifecycle.State.STARTED) }
                .distinctUntilChanged()
            combine(
                settingsRepository.settings.map { it.backgroundAlerts }.distinctUntilChanged(),
                pairingRepository.pairing.map { it != null }.distinctUntilChanged(),
                visible,
            ) { enabled, paired, isVisible -> (enabled && paired) to isVisible }
                .collect { (wanted, isVisible) ->
                    val intent = Intent(context, AgentMonitorService::class.java)
                    when {
                        !wanted -> context.stopService(intent)
                        isVisible -> runCatching { ContextCompat.startForegroundService(context, intent) }
                    }
                }
        }
    }
}
