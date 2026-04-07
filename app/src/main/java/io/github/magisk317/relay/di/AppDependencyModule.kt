package io.github.magisk317.relay.di

import io.github.magisk317.relay.app.*
import io.github.magisk317.relay.app.AppInitializer
import org.koin.dsl.bind
import org.koin.dsl.module

val appDependencyModule = module {
    // Initializers
    single { InfrastructureInitializer() } bind AppInitializer::class
    single { SecurityInitializer() } bind AppInitializer::class
    single { DataStoreSyncInitializer() } bind AppInitializer::class
    single { ConfigDiagnosticsInitializer() } bind AppInitializer::class
    single { ServiceMonitorInitializer() } bind AppInitializer::class
    single { LifecycleMonitorInitializer() } bind AppInitializer::class
    single { RemoteAgentInitializer() } bind AppInitializer::class
    single { InstallMonitorInitializer() } bind AppInitializer::class
}
