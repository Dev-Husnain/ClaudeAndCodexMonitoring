package com.claude.codex.ai.monitoring

import android.app.Application
import com.claude.codex.ai.monitoring.di.appModule
import com.claude.codex.ai.monitoring.di.dataModule
import com.claude.codex.ai.monitoring.di.domainModule
import com.claude.codex.ai.monitoring.di.presentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@App)
            modules(appModule, dataModule, domainModule, presentationModule)
        }
    }
}
