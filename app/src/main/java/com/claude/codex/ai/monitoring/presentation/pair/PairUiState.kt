package com.claude.codex.ai.monitoring.presentation.pair

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.claude.codex.ai.monitoring.core.utils.UiText

@Immutable
data class PairUiState(
    val step: PairStep = PairStep.Scan,
    val showCodeEntry: Boolean = false,
    val codeInput: String = "",
    @param:StringRes val codeError: Int? = null,
    val deviceName: String = "",
    @param:StringRes val nameError: Int? = null,
)

/** The pairing flow, one step at a time (spec 9.3 #1). */
sealed interface PairStep {
    data object Scan : PairStep

    data class Confirm(val offer: PairOfferUiModel) : PairStep

    data class Waiting(val computerName: String) : PairStep

    data class Success(val computerName: String, val canSendInput: Boolean) : PairStep

    data class Failed(val message: UiText) : PairStep
}

@Immutable
data class PairOfferUiModel(
    val computerName: String,
    val address: String,
    val desktopKey: String,
    val phoneKey: String,
)
