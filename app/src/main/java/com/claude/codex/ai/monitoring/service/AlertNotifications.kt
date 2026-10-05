package com.claude.codex.ai.monitoring.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.claude.codex.ai.monitoring.MainActivity
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.domain.models.SessionAlertKind
import com.claude.codex.ai.monitoring.domain.models.SessionAlertModel

/**
 * The two notification channels and their notifications. Alerts carry only the project name and why the
 * session waits, never prompt, tool or terminal text (spec: no sensitive content), and the lock screen
 * shows a version without even the project name.
 */
object AlertNotifications {

    const val CHANNEL_CONNECTION = "connection"
    const val CHANNEL_ALERTS = "alerts"
    const val ONGOING_ID = 1
    const val EXTRA_SESSION_ID = "com.claude.codex.ai.monitoring.SESSION_ID"

    fun createChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_CONNECTION, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.channel_connection_name))
                .setDescription(context.getString(R.string.channel_connection_description))
                .setShowBadge(false)
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ALERTS, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.channel_alerts_name))
                .setDescription(context.getString(R.string.channel_alerts_description))
                .build(),
        )
    }

    /** The quiet, permanent notification of the foreground service. */
    fun ongoing(context: Context, title: String, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_CONNECTION)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(context, sessionId = null))
            .addAction(
                0,
                context.getString(R.string.notif_turn_off),
                PendingIntent.getService(
                    context,
                    REQUEST_TURN_OFF,
                    Intent(context, AgentMonitorService::class.java).setAction(AgentMonitorService.ACTION_TURN_OFF),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()

    fun alert(context: Context, alert: SessionAlertModel): Notification {
        val title = context.getString(
            when (alert.kind) {
                SessionAlertKind.PERMISSION -> R.string.alert_permission_title
                SessionAlertKind.REPLY -> R.string.alert_reply_title
                SessionAlertKind.INPUT -> R.string.alert_input_title
                SessionAlertKind.ERROR -> R.string.alert_error_title
            },
        )
        val lockScreen = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(context.getString(R.string.alert_public_title))
            .build()
        return NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(alert.projectName ?: context.getString(R.string.session_default_name))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreen)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, alert.sessionId))
            .build()
    }

    /** One notification per session, so a newer reason replaces the older one. */
    fun alertId(sessionId: String): Int = ALERT_ID_BASE + (sessionId.hashCode() and ALERT_ID_MASK)

    private fun openApp(context: Context, sessionId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { sessionId?.let { putExtra(EXTRA_SESSION_ID, it) } }
        return PendingIntent.getActivity(
            context,
            sessionId?.let(::alertId) ?: REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private const val REQUEST_OPEN_APP = 0
    private const val REQUEST_TURN_OFF = 1
    private const val ALERT_ID_BASE = 1_000
    private const val ALERT_ID_MASK = 0xFFFFF
}
