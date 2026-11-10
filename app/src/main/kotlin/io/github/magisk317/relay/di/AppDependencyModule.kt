package io.github.magisk317.relay.di

import io.github.magisk317.relay.app.*
import io.github.magisk317.relay.app.AppInitializer
import io.github.magisk317.relay.feature.mode.RuntimeSignalRefresher
import io.github.magisk317.relay.ui.app.XposedRuntimeSignals
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.bind
import org.koin.dsl.module

val appDependencyModule = module {
    single { XposedRuntimeSignals(androidApplication()) } bind RuntimeSignalRefresher::class

    // Initializers
    single { InfrastructureInitializer(get()) } bind AppInitializer::class
    single { SecurityInitializer() } bind AppInitializer::class
    single { DataStoreSyncInitializer() } bind AppInitializer::class
    single { CodeRecordImportInitializer() } bind AppInitializer::class
    single { ConfigDiagnosticsInitializer() } bind AppInitializer::class
    single { ServiceMonitorInitializer() } bind AppInitializer::class
    single { LifecycleMonitorInitializer() } bind AppInitializer::class
    single { RemoteAgentInitializer() } bind AppInitializer::class
}
