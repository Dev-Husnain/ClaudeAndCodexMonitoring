package com.claude.codex.ai.monitoring

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.claude.codex.ai.monitoring.core.navigation.PendingNavigation
import com.claude.codex.ai.monitoring.presentation.root.AppRoot
import com.claude.codex.ai.monitoring.service.AlertNotifications
import org.koin.android.ext.android.inject

/** Hosts the app only. No colours, padding or insets here: every screen owns its own. */
class MainActivity : ComponentActivity() {
    private val pendingNavigation: PendingNavigation by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openSessionFrom(intent)
        setContent { AppRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openSessionFrom(intent)
    }

    /** A tapped alert names the session to open. */
    private fun openSessionFrom(intent: Intent?) {
        intent?.getStringExtra(AlertNotifications.EXTRA_SESSION_ID)?.let(pendingNavigation::openSession)
    }
}
