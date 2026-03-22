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
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.domain.pipeline.DispatchResultWriter
import io.github.magisk317.relay.domain.service.*
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {
    single { RuntimeGraph.from(get()) }

    single { get<RuntimeGraph>().database }
    single<PreferenceDataSource> { get<RuntimeGraph>().preferenceDataSource }

    single<SettingsRepository> { get<RuntimeGraph>().settingsRepository }
    single<RelayRecordRepository> { get<RuntimeGraph>().relayRecordRepository }
    single<AnalyticsRepository> { get<RuntimeGraph>().analyticsRepository }
    single<ConfigRepository> { get<RuntimeGraph>().configRepository }

    single<SystemInfoProvider> { get<RuntimeGraph>().systemInfoProvider }
    single<MessageFormatter> { get<RuntimeGraph>().messageFormatter }
    single<DispatchResultWriter> { get<RuntimeGraph>().dispatchResultWriter }
    single { get<RuntimeGraph>().eventGatekeeper }
    single { get<RuntimeGraph>().routingResolver }
    single { get<RuntimeGraph>().senderSelector }
    single { get<RuntimeGraph>().dispatchExecutor }
    single { get<RuntimeGraph>().eventPipeline }
    // ViewModels
    viewModelOf(::AppConfigViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::CodeRecordViewModel)
    viewModelOf(::RuleViewModel)
    viewModelOf(::SenderViewModel)
}
