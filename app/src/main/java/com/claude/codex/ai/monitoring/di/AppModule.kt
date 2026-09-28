package com.claude.codex.ai.monitoring.di

import com.claude.codex.ai.monitoring.BuildConfig
import com.claude.codex.ai.monitoring.core.utils.AppDispatchers
import com.claude.codex.ai.monitoring.core.utils.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module

val AppVersion = named("appVersion")
val AppScope = named("appScope")

val appModule = module {
    single { AppDispatchers(io = Dispatchers.IO, default = Dispatchers.Default) }
    single { Clock.System }
    single(AppVersion) { BuildConfig.VERSION_NAME }
    // Process-lifetime scope for the shared agent connection; owned by the Application.
    single<CoroutineScope>(AppScope) { CoroutineScope(SupervisorJob() + get<AppDispatchers>().io) }
}
