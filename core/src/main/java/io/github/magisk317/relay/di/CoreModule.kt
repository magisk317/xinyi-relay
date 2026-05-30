package io.github.magisk317.relay.di

import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.android.data.datasource.PreferenceDataSourceImpl
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.android.prefs.PrefsReader
import io.github.magisk317.relay.android.service.SystemInfoProviderImpl
import io.github.magisk317.relay.app.sender.SenderTestService
import io.github.magisk317.relay.contract.repository.RemoteSyncRepository
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.data.repository.AnalyticsRepository
import io.github.magisk317.relay.data.repository.ConfigRepository
import io.github.magisk317.relay.data.repository.RelayRecordRepository
import io.github.magisk317.relay.data.repository.RemoteAgentRepository
import io.github.magisk317.relay.data.repository.ScheduledTaskRepositoryImpl
import io.github.magisk317.relay.data.repository.SettingsRepository
import io.github.magisk317.relay.domain.pipeline.DispatchExecutor
import io.github.magisk317.relay.domain.pipeline.DispatchResultWriter
import io.github.magisk317.relay.domain.pipeline.EventGatekeeper
import io.github.magisk317.relay.domain.pipeline.EventPipeline
import io.github.magisk317.relay.domain.pipeline.RoutingResolver
import io.github.magisk317.relay.domain.system.RuntimeRecordFacade
import io.github.magisk317.relay.engine.pipeline.SenderSelector
import io.github.magisk317.relay.engine.service.AppConfigRepository
import io.github.magisk317.relay.engine.service.MessageFormatter
import io.github.magisk317.relay.engine.service.MessageRecordRepository
import io.github.magisk317.relay.engine.service.RuntimeAnalyticsProvider
import io.github.magisk317.relay.engine.service.ScheduledTaskRepository
import io.github.magisk317.relay.engine.service.SenderRuntimeServiceRegistry
import io.github.magisk317.relay.engine.service.SystemInfoProvider
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Single source of truth for the application object graph.
 *
 * Every runtime singleton is constructed here exactly once. Non-UI code reaches
 * these singletons through RuntimeGraph, which is a thin typed facade that
 * resolves each member from this Koin container, so there is no separate
 * hand-maintained construction list to keep in sync.
 */
val coreModule = module {
    single { AppDatabase.getInstance(androidContext()) }
    single<PreferenceDataSource> { PreferenceDataSourceImpl(androidContext()) }

    single<SettingsPreferencesRepository> { SettingsRepository(androidContext(), get()) }
    single<MessageRecordRepository> { RelayRecordRepository(androidContext(), get(), get()) }
    single<RuntimeAnalyticsProvider> { AnalyticsRepository(androidContext(), get()) }
    single<RemoteSyncRepository> { RemoteAgentRepository(androidContext(), get()) }
    single { RuntimeRecordFacade(androidContext(), get(), get<MessageRecordRepository>()) }
    single<AppConfigRepository> {
        val db = get<AppDatabase>()
        ConfigRepository(
            context = androidContext(),
            db = db,
            smsCodeRuleDao = db.smsCodeRuleDao(),
            appInfoDao = db.appInfoDao(),
            notifyRouteRuleDao = db.notifyRouteRuleDao(),
            forwardFilterRuleDao = db.forwardFilterRuleDao(),
            ruleDao = db.ruleDao(),
            senderDao = db.senderDao(),
        )
    }
    single<ScheduledTaskRepository> { ScheduledTaskRepositoryImpl(androidContext(), get()) }

    single<SystemInfoProvider> { SystemInfoProviderImpl(androidContext()) }
    single {
        MessageFormatter(
            systemInfoProvider = get(),
            simSlotRemarkResolver = { simSlot -> PrefsReader.getSimSlotRemark(androidContext(), simSlot) },
        )
    }
    single { EventGatekeeper(get(), get()) }
    single { RoutingResolver(get()) }
    single { SenderSelector() }
    single {
        DispatchExecutor {
            SenderRuntimeServiceRegistry.requireInstalled().createDispatcher(androidContext())
        }
    }
    single { DispatchResultWriter(get(), get(), get()) }
    single {
        EventPipeline(
            db = get(),
            eventGatekeeper = get(),
            routingResolver = get(),
            senderSelector = get(),
            dispatchExecutor = get(),
            dispatchResultWriter = get(),
            messageFormatter = get(),
            systemInfoProvider = get(),
            settingsRepository = get(),
            preferenceDataSource = get(),
            messageSyncTrigger = get<RemoteSyncRepository>()::scheduleMessageTriggeredSync,
        )
    }
    single { SenderTestService(androidContext()) }
}
