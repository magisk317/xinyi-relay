package io.github.magisk317.relay.di

import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.contract.repository.RemoteSyncRepository
import io.github.magisk317.relay.engine.service.AppConfigRepository
import io.github.magisk317.relay.engine.service.MessageRecordRepository
import io.github.magisk317.relay.engine.service.RuntimeAnalyticsProvider
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.domain.pipeline.DispatchResultWriter
import io.github.magisk317.relay.auth.FirebaseAuthManager
import io.github.magisk317.relay.auth.GoogleSignInHelper
import io.github.magisk317.relay.engine.service.*
import org.koin.dsl.module

val coreModule = module {
    single { RuntimeGraph.from(get()) }

    single { get<RuntimeGraph>().database }
    single<PreferenceDataSource> { get<RuntimeGraph>().preferenceDataSource }

    single<SettingsPreferencesRepository> { get<RuntimeGraph>().settingsRepository }
    single<MessageRecordRepository> { get<RuntimeGraph>().relayRecordRepository }
    single<RuntimeAnalyticsProvider> { get<RuntimeGraph>().analyticsRepository }
    single<AppConfigRepository> { get<RuntimeGraph>().configRepository }
    single<RemoteSyncRepository> { get<RuntimeGraph>().remoteAgentRepository }
    single { get<RuntimeGraph>().scheduledTaskRepository }

    single<SystemInfoProvider> { get<RuntimeGraph>().systemInfoProvider }
    single<MessageFormatter> { get<RuntimeGraph>().messageFormatter }
    single<DispatchResultWriter> { get<RuntimeGraph>().dispatchResultWriter }
    single { get<RuntimeGraph>().eventGatekeeper }
    single { get<RuntimeGraph>().routingResolver }
    single { get<RuntimeGraph>().senderSelector }
    single { get<RuntimeGraph>().dispatchExecutor }
    single { get<RuntimeGraph>().eventPipeline }

    single { GoogleSignInHelper(get()) }
    single { FirebaseAuthManager(get(), get()) }
}
