package com.claude.codex.ai.monitoring.service

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.domain.models.AgentSnapshotModel
import com.claude.codex.ai.monitoring.domain.models.ConnectionStatus
import com.claude.codex.ai.monitoring.domain.models.SessionStatus
import com.claude.codex.ai.monitoring.domain.repo.AgentRepository
import com.claude.codex.ai.monitoring.domain.repo.SettingsRepository
import com.claude.codex.ai.monitoring.domain.usecase.DetectSessionAlertsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Keeps the connection to the computer open while the app is closed (spec phase 7, option A) and
 * notifies when a session needs the user. Collecting [AgentRepository.snapshot] is what keeps the
 * shared connection alive. Type `connectedDevice`: a network link to the user's own computer, with no
 * daily time limit (unlike `dataSync`).
 */
class AgentMonitorService : Service() {

    private val agentRepository: AgentRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val detectAlerts: DetectSessionAlertsUseCase by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null
    private var lastOngoing: Pair<String, String>? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TURN_OFF) {
            scope.launch {
                settingsRepository.setBackgroundAlerts(false)
                stopSelf()
            }
            return START_NOT_STICKY
        }
        AlertNotifications.createChannels(this)
        val title = getString(R.string.notif_connecting)
        val text = getString(R.string.notif_alerts_on)
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                AlertNotifications.ONGOING_ID,
                AlertNotifications.ongoing(this, title, text),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
            )
        }.isSuccess
        // Android refuses foreground services started from the background; the app retries when opened.
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        lastOngoing = title to text
        if (watcher == null) watcher = scope.launch { watch() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun watch() {
        var previous: AgentSnapshotModel? = null
        agentRepository.snapshot.collect { snapshot ->
            val changes = detectAlerts(previous, snapshot)
            previous = snapshot
            val manager = NotificationManagerCompat.from(this)
            changes.cleared.forEach { manager.cancel(AlertNotifications.alertId(it)) }
            // While the app is on screen the user already sees what needs them.
            if (!isAppVisible()) {
                changes.raised.forEach { post(AlertNotifications.alertId(it.sessionId), AlertNotifications.alert(this, it)) }
            }
            updateOngoing(snapshot)
        }
    }

    private fun updateOngoing(snapshot: AgentSnapshotModel) {
        val computer = snapshot.computer?.name ?: getString(R.string.home_your_computer)
        val title = when (snapshot.connection) {
            is ConnectionStatus.Connected -> getString(R.string.notif_connected, computer)
            ConnectionStatus.Connecting, is ConnectionStatus.Reconnecting -> getString(R.string.notif_connecting)
            is ConnectionStatus.Offline -> getString(R.string.notif_offline, computer)
            is ConnectionStatus.Unauthorized -> getString(R.string.notif_unauthorized)
        }
        val waiting = snapshot.sessions.count { it.awaiting != null || it.status == SessionStatus.WAITING_INPUT }
        val text = if (waiting > 0) resources.getQuantityString(R.plurals.notif_sessions_waiting, waiting, waiting) else getString(R.string.notif_alerts_on)
        if (lastOngoing == title to text) return
        lastOngoing = title to text
        post(AlertNotifications.ONGOING_ID, AlertNotifications.ongoing(this, title, text))
    }

    /** Posts only when allowed: on Android 13+ the user may have refused notifications. */
    private fun post(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(this).notify(id, notification)
    }

    private fun isAppVisible(): Boolean = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    companion object {
        const val ACTION_TURN_OFF = "com.claude.codex.ai.monitoring.TURN_OFF_ALERTS"
    }
}
