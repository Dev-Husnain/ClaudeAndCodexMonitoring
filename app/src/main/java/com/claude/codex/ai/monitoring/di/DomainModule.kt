package com.claude.codex.ai.monitoring.di

import com.claude.codex.ai.monitoring.domain.usecase.ObserveSessionDetailUseCase
import com.claude.codex.ai.monitoring.domain.usecase.ObserveSessionOverviewUseCase
import com.claude.codex.ai.monitoring.domain.usecase.PairDeviceUseCase
import com.claude.codex.ai.monitoring.domain.usecase.ParsePairingCodeUseCase
import com.claude.codex.ai.monitoring.domain.usecase.SendInstructionUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val domainModule = module {
    factoryOf(::ObserveSessionOverviewUseCase)
    factoryOf(::ObserveSessionDetailUseCase)
    factoryOf(::ParsePairingCodeUseCase)
    factoryOf(::PairDeviceUseCase)
    factoryOf(::SendInstructionUseCase)
}
