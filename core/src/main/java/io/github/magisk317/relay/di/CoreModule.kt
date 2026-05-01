package io.github.magisk317.relay.di

import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.data.repository.AnalyticsRepository
import io.github.magisk317.relay.data.repository.ConfigRepository
import io.github.magisk317.relay.data.repository.RemoteAgentRepository
import io.github.magisk317.relay.data.repository.RelayRecordRepository
import io.github.magisk317.relay.data.repository.SettingsRepository
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.domain.pipeline.DispatchResultWriter
import io.github.magisk317.relay.engine.service.*
import org.koin.dsl.module

val coreModule = module {
    single { RuntimeGraph.from(get()) }

    single { get<RuntimeGraph>().database }
    single<PreferenceDataSource> { get<RuntimeGraph>().preferenceDataSource }

    single<SettingsRepository> { get<RuntimeGraph>().settingsRepository }
    single<RelayRecordRepository> { get<RuntimeGraph>().relayRecordRepository }
    single<AnalyticsRepository> { get<RuntimeGraph>().analyticsRepository }
    single<ConfigRepository> { get<RuntimeGraph>().configRepository }
    single<RemoteAgentRepository> { get<RuntimeGraph>().remoteAgentRepository }

    single<SystemInfoProvider> { get<RuntimeGraph>().systemInfoProvider }
    single<MessageFormatter> { get<RuntimeGraph>().messageFormatter }
    single<DispatchResultWriter> { get<RuntimeGraph>().dispatchResultWriter }
    single { get<RuntimeGraph>().eventGatekeeper }
    single { get<RuntimeGraph>().routingResolver }
    single { get<RuntimeGraph>().senderSelector }
    single { get<RuntimeGraph>().dispatchExecutor }
    single { get<RuntimeGraph>().eventPipeline }
}
