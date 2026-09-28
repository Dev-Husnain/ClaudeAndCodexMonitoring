package com.claude.codex.ai.monitoring.di

import com.claude.codex.ai.monitoring.presentation.devicessecurity.DevicesSecurityViewModel
import com.claude.codex.ai.monitoring.presentation.home.HomeViewModel
import com.claude.codex.ai.monitoring.presentation.pair.PairViewModel
import com.claude.codex.ai.monitoring.presentation.root.RootViewModel
import com.claude.codex.ai.monitoring.presentation.sessiondetail.SessionDetailViewModel
import com.claude.codex.ai.monitoring.presentation.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule = module {
    viewModelOf(::RootViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::PairViewModel)
    viewModelOf(::DevicesSecurityViewModel)
    viewModel { params ->
        SessionDetailViewModel(
            sessionId = params.get(),
            observeSessionDetail = get(),
            agentRepository = get(),
            clock = get(),
        )
    }
    viewModel { SettingsViewModel(settingsRepository = get(), appVersion = get(AppVersion)) }
}
