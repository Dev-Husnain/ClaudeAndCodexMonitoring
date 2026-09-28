package com.claude.codex.ai.monitoring.di

import com.claude.codex.ai.monitoring.data.local.SettingsDataSource
import com.claude.codex.ai.monitoring.data.network.AgentSocketDataSource
import com.claude.codex.ai.monitoring.data.network.ReconnectBackoff
import com.claude.codex.ai.monitoring.data.repo.AgentRepositoryImpl
import com.claude.codex.ai.monitoring.data.repo.SettingsRepositoryImpl
import com.claude.codex.ai.monitoring.domain.repo.AgentRepository
import com.claude.codex.ai.monitoring.domain.repo.SettingsRepository
import com.claude.codex.ai.monitoring.protocol.ProtocolCodec
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataModule = module {
    single { ProtocolCodec() }
    single { HttpClient(CIO) { install(WebSockets) } }
    single { ReconnectBackoff() }
    single { SettingsDataSource(androidContext()) }
    single { AgentSocketDataSource(client = get(), codec = get()) }

    single<SettingsRepository> { SettingsRepositoryImpl(dataSource = get()) }
    single<AgentRepository> {
        AgentRepositoryImpl(
            socket = get(),
            settingsRepository = get(),
            appScope = get(AppScope),
            backoff = get(),
            clock = get(),
            appVersion = get(AppVersion),
        )
    }
}
