package com.claude.codex.ai.monitoring.presentation.devicessecurity

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.claude.codex.ai.monitoring.R

@Immutable
data class DevicesSecurityUiState(
    val computerName: String = "",
    val address: String = "",
    val desktopKey: String = "",
    @param:StringRes val access: Int = R.string.devices_access_read_only,
    val pairedOn: String = "",
    val phoneName: String = "",
    val phoneKey: String = "",
    val showUnpairConfirm: Boolean = false,
)
