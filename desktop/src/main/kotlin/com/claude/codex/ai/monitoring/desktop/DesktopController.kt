package com.claude.codex.ai.monitoring.desktop

import com.claude.codex.ai.monitoring.desktop.devices.AuditCategory
import com.claude.codex.ai.monitoring.desktop.devices.AuditLog
import com.claude.codex.ai.monitoring.desktop.devices.DeviceGrant
import com.claude.codex.ai.monitoring.desktop.devices.DeviceStore
import com.claude.codex.ai.monitoring.desktop.devices.PairedDevice
import com.claude.codex.ai.monitoring.desktop.pairing.PairingDecision
import com.claude.codex.ai.monitoring.desktop.pairing.PairingManager
import com.claude.codex.ai.monitoring.desktop.security.DesktopIdentity
import com.claude.codex.ai.monitoring.desktop.server.ConnectionHub
import com.claude.codex.ai.monitoring.desktop.session.SessionRegistry
import com.claude.codex.ai.monitoring.protocol.ProtocolConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** How the phone will reach this computer; decides the address inside the pairing QR. */
enum class PairingRoute { TUNNEL, USB }

/** Actions the desktop UI can take. Anything that changes access applies to live connections at once. */
class DesktopController(
    val registry: SessionRegistry,
    val hub: ConnectionHub,
    val devices: DeviceStore,
    val audit: AuditLog,
    val pairing: PairingManager,
    val identity: DesktopIdentity,
    val publicUrl: String,
    val demoMode: Boolean,
    private val scope: CoroutineScope,
) {
    fun baseUrlFor(route: PairingRoute): String = when (route) {
        PairingRoute.TUNNEL -> publicUrl
        PairingRoute.USB -> "http://${ProtocolConstants.LOOPBACK_HOST}:${ProtocolConstants.DEFAULT_PORT}"
    }

    fun startPairing(route: PairingRoute) {
        pairing.createOffer(baseUrlFor(route))
    }

    fun cancelPairing() = pairing.cancelOffer()

    fun approve(grant: DeviceGrant) = pairing.decide(PairingDecision.Approve(grant))

    fun reject() = pairing.decide(PairingDecision.Reject)

    fun updateGrant(device: PairedDevice, grant: DeviceGrant) {
        scope.launch {
            devices.updateGrant(device.deviceId, grant)
            audit.record(AuditCategory.ACCESS, "Access changed for \"${device.name}\"", device.deviceId)
            hub.reconnect(device.deviceId)
        }
    }

    fun revoke(device: PairedDevice) {
        scope.launch {
            devices.remove(device.deviceId)
            audit.record(AuditCategory.ACCESS, "Revoked \"${device.name}\"", device.deviceId)
            hub.revoke(device.deviceId)
        }
    }
}
