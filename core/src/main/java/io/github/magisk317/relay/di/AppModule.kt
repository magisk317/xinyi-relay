package io.github.magisk317.relay.di

import io.github.magisk317.relay.app.*
import io.github.magisk317.relay.ui.home.AppConfigViewModel
import io.github.magisk317.relay.ui.home.SettingsViewModel
import io.github.magisk317.relay.ui.record.CodeRecordViewModel
import io.github.magisk317.relay.ui.rule.RuleViewModel
import io.github.magisk317.relay.ui.sender.SenderViewModel
import io.github.magisk317.relay.app.AppInitializer
import io.github.magisk317.relay.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.data.db.AppDatabase
import io.github.magisk317.relay.data.repository.AnalyticsRepository
import io.github.magisk317.relay.data.repository.SettingsRepository
import io.github.magisk317.relay.data.repository.RelayRecordRepository
import io.github.magisk317.relay.data.repository.ConfigRepository
import io.github.magisk317.relay.domain.pipeline.StorageRuntimeGraph
import io.github.magisk317.relay.domain.pipeline.DispatchResultWriter
import io.github.magisk317.relay.domain.service.*
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {
    single { StorageRuntimeGraph.from(get()) }

    single { get<StorageRuntimeGraph>().database }
    single<PreferenceDataSource> { get<StorageRuntimeGraph>().preferenceDataSource }

    single<SettingsRepository> { get<StorageRuntimeGraph>().settingsRepository }
    single<RelayRecordRepository> { get<StorageRuntimeGraph>().relayRecordRepository }
    single<AnalyticsRepository> { get<StorageRuntimeGraph>().analyticsRepository }
    single<ConfigRepository> { get<StorageRuntimeGraph>().configRepository }

    single<SystemInfoProvider> { get<StorageRuntimeGraph>().systemInfoProvider }
    single<MessageFormatter> { get<StorageRuntimeGraph>().messageFormatter }
    single<DispatchResultWriter> { get<StorageRuntimeGraph>().dispatchResultWriter }
    single { get<StorageRuntimeGraph>().eventGatekeeper }
    single { get<StorageRuntimeGraph>().routingResolver }
    single { get<StorageRuntimeGraph>().senderSelector }
    single { get<StorageRuntimeGraph>().dispatchExecutor }
    single { get<StorageRuntimeGraph>().eventPipeline }
    // ViewModels
    viewModelOf(::AppConfigViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::CodeRecordViewModel)
    viewModelOf(::RuleViewModel)
    viewModelOf(::SenderViewModel)
}
