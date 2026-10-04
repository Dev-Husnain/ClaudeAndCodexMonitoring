package com.claude.codex.ai.monitoring.di

import android.os.Build
import android.provider.Settings
import com.claude.codex.ai.monitoring.data.local.HiddenSessionsDataSource
import com.claude.codex.ai.monitoring.data.local.PairingDataSource
import com.claude.codex.ai.monitoring.data.repo.HiddenSessionsRepositoryImpl
import com.claude.codex.ai.monitoring.domain.repo.HiddenSessionsRepository
import com.claude.codex.ai.monitoring.data.local.SettingsDataSource
import com.claude.codex.ai.monitoring.data.network.AgentSocketDataSource
import com.claude.codex.ai.monitoring.data.network.PairingApi
import com.claude.codex.ai.monitoring.data.network.ReconnectBackoff
import com.claude.codex.ai.monitoring.data.repo.AgentRepositoryImpl
import com.claude.codex.ai.monitoring.data.repo.PairingRepositoryImpl
import com.claude.codex.ai.monitoring.data.repo.SettingsRepositoryImpl
import com.claude.codex.ai.monitoring.data.security.DeviceKeyDataSource
import com.claude.codex.ai.monitoring.domain.repo.AgentRepository
import com.claude.codex.ai.monitoring.domain.repo.PairingRepository
import com.claude.codex.ai.monitoring.domain.repo.SettingsRepository
import com.claude.codex.ai.monitoring.protocol.ProtocolCodec
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataModule = module {
    single { ProtocolCodec() }
    single {
        HttpClient(CIO) {
            install(WebSockets)
            install(HttpTimeout) { connectTimeoutMillis = CONNECT_TIMEOUT_MS }
        }
    }
    single { ReconnectBackoff() }
    single { SettingsDataSource(androidContext()) }
    single { PairingDataSource(androidContext()) }
    single { HiddenSessionsDataSource(androidContext()) }
    single<HiddenSessionsRepository> { HiddenSessionsRepositoryImpl(dataSource = get()) }
    single { DeviceKeyDataSource() }
    single { PairingApi(client = get()) }
    single { AgentSocketDataSource(client = get(), codec = get()) }

    single<SettingsRepository> { SettingsRepositoryImpl(dataSource = get()) }
    single<PairingRepository> {
        val context = androidContext()
        PairingRepositoryImpl(
            keys = get(),
            store = get(),
            api = get(),
            dispatchers = get(),
            clock = get(),
            systemDeviceName = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
                } else {
                    null
                }
            },
        )
    }
    single<AgentRepository> {
        AgentRepositoryImpl(
            socket = get(),
            keys = get(),
            pairingRepository = get(),
            appScope = get(AppScope),
            backoff = get(),
            clock = get(),
            appVersion = get(AppVersion),
        )
    }
}

private const val CONNECT_TIMEOUT_MS = 15_000L
