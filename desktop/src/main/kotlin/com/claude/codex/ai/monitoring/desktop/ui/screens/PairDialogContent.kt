package com.claude.codex.ai.monitoring.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.claude.codex.ai.monitoring.desktop.PairingRoute
import com.claude.codex.ai.monitoring.desktop.pairing.PairingOffer
import com.claude.codex.ai.monitoring.desktop.ui.components.CountdownRing
import com.claude.codex.ai.monitoring.desktop.ui.components.OutlineButton
import com.claude.codex.ai.monitoring.desktop.ui.components.PrimaryButton
import com.claude.codex.ai.monitoring.desktop.ui.components.QrCode
import com.claude.codex.ai.monitoring.desktop.ui.components.ToggleChip
import com.claude.codex.ai.monitoring.desktop.ui.theme.DesktopTheme
import com.claude.codex.ai.monitoring.protocol.AgentCrypto
import kotlinx.coroutines.delay
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** Pair a phone: large QR, the route it encodes, a countdown ring and a copyable code. */
@Composable
fun PairDialogContent(
    offer: PairingOffer,
    route: PairingRoute,
    /** Null while no phone access address is set: only USB pairing works. */
    tunnelHost: String?,
    tunnelReachable: Boolean?,
    desktopFingerprint: String,
    onRouteChange: (PairingRoute) -> Unit,
    onRegenerate: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = DesktopTheme.colors
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(offer) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val remaining = offer.expiresAtMs - now
    val expired = remaining <= 0

    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Pair a phone", style = MaterialTheme.typography.headlineMedium.copy(brush = colors.brandGradient))
        Text(
            "Open AgentMon on the phone and scan this code. You will approve the phone here before it gets any access.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleChip("Anywhere · ${tunnelHost ?: "not set up"}", route == PairingRoute.TUNNEL, { onRouteChange(PairingRoute.TUNNEL) })
            ToggleChip("USB · adb reverse", route == PairingRoute.USB, { onRouteChange(PairingRoute.USB) })
        }
        val routeNote = when {
            tunnelHost == null ->
                "To pair for use anywhere, set your tunnel address under Overview > This computer > Phone access address. " +
                    "This code uses USB: connect the phone and run: adb reverse tcp:8787 tcp:8787"
            route == PairingRoute.USB -> "Phone must be connected by USB with: adb reverse tcp:8787 tcp:8787"
            tunnelReachable == false ->
                "This computer could not reach $tunnelHost just now (its DNS or the tunnel). If https://$tunnelHost/health " +
                    "opens in the phone's browser, pairing still works."
            tunnelReachable == null -> "Works from anywhere through your tunnel. Checking $tunnelHost…"
            else -> "Works from anywhere through your tunnel."
        }
        Text(
            routeNote,
            style = MaterialTheme.typography.bodySmall,
            color = if (route == PairingRoute.TUNNEL && tunnelReachable == false) colors.error else colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (expired) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(40.dp)) {
                Text("This code expired", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                PrimaryButton("New code", onClick = onRegenerate)
            }
        } else {
            QrCode(text = offer.code, size = 220.dp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CountdownRing(remainingMs = remaining, totalMs = offer.expiresAtMs - offer.createdAtMs)
                Column {
                    Text("Single use, expires in 2 minutes", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text(
                        "Desktop key  ${AgentCrypto.shortFingerprint(desktopFingerprint)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary,
                    )
                }
            }
            Text("No camera? Paste this code in the app:", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            SelectionContainer {
                Text(
                    offer.code,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceElevated, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!expired) {
                OutlineButton("Copy code", onClick = {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(offer.code), null)
                })
            }
            OutlineButton("Close", onClick = onClose, color = colors.textSecondary)
        }
    }
}
