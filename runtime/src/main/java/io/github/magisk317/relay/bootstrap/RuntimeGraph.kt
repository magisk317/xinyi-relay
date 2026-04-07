package io.github.magisk317.relay.bootstrap

import android.content.Context
import io.github.magisk317.relay.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.data.datasource.PreferenceDataSourceImpl
import io.github.magisk317.relay.data.db.AppDatabase
import io.github.magisk317.relay.data.repository.AnalyticsRepository
import io.github.magisk317.relay.data.repository.ConfigRepository
import io.github.magisk317.relay.data.repository.RelayRecordRepository
import io.github.magisk317.relay.data.repository.RemoteAgentRepository
import io.github.magisk317.relay.data.repository.SettingsRepository
import io.github.magisk317.relay.domain.pipeline.DispatchExecutor
import io.github.magisk317.relay.domain.pipeline.DispatchResultWriter
import io.github.magisk317.relay.domain.pipeline.EventGatekeeper
import io.github.magisk317.relay.domain.pipeline.EventPipeline
import io.github.magisk317.relay.domain.pipeline.RoutingResolver
import io.github.magisk317.relay.domain.pipeline.SenderSelector
import io.github.magisk317.relay.domain.service.MessageFormatter
import io.github.magisk317.relay.domain.service.SystemInfoProvider
import io.github.magisk317.relay.domain.service.SystemInfoProviderImpl
import io.github.magisk317.relay.domain.system.RuntimeRecordFacade

class RuntimeGraph private constructor(
    context: Context,
) {
    private val appContext = context.applicationContext ?: context

    val database: AppDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppDatabase.getInstance(appContext)
    }

    val preferenceDataSource: PreferenceDataSource by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PreferenceDataSourceImpl(appContext)
    }

    val settingsRepository: SettingsRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SettingsRepository(appContext, preferenceDataSource)
    }

    val relayRecordRepository: RelayRecordRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RelayRecordRepository(appContext, database, preferenceDataSource)
    }

    val analyticsRepository: AnalyticsRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AnalyticsRepository(appContext, database)
    }

    val remoteAgentRepository: RemoteAgentRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RemoteAgentRepository(appContext, preferenceDataSource)
    }

    val runtimeRecordFacade: RuntimeRecordFacade by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RuntimeRecordFacade(appContext, database, relayRecordRepository)
    }

    val configRepository: ConfigRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ConfigRepository(
            context = appContext,
            db = database,
            smsCodeRuleDao = database.smsCodeRuleDao(),
            appInfoDao = database.appInfoDao(),
            notifyRouteRuleDao = database.notifyRouteRuleDao(),
            forwardFilterRuleDao = database.forwardFilterRuleDao(),
            ruleDao = database.ruleDao(),
            senderDao = database.senderDao(),
        )
    }

    val systemInfoProvider: SystemInfoProvider by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SystemInfoProviderImpl(appContext)
    }

    val messageFormatter: MessageFormatter by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        MessageFormatter(
            systemInfoProvider = systemInfoProvider,
            simSlotRemarkResolver = { simSlot ->
                io.github.magisk317.relay.prefs.PrefsReader.getSimSlotRemark(appContext, simSlot)
            },
        )
    }

    val eventGatekeeper: EventGatekeeper by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        EventGatekeeper(database, preferenceDataSource)
    }

    val routingResolver: RoutingResolver by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoutingResolver(database)
    }

    val senderSelector: SenderSelector by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SenderSelector()
    }

    val dispatchExecutor: DispatchExecutor by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DispatchExecutor(appContext)
    }

    val dispatchResultWriter: DispatchResultWriter by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DispatchResultWriter(database, relayRecordRepository, preferenceDataSource)
    }

    val eventPipeline: EventPipeline by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        EventPipeline(
            db = database,
            eventGatekeeper = eventGatekeeper,
            routingResolver = routingResolver,
            senderSelector = senderSelector,
            dispatchExecutor = dispatchExecutor,
            dispatchResultWriter = dispatchResultWriter,
            messageFormatter = messageFormatter,
            systemInfoProvider = systemInfoProvider,
            settingsRepository = settingsRepository,
            preferenceDataSource = preferenceDataSource,
            messageSyncTrigger = remoteAgentRepository::scheduleMessageTriggeredSync,
        )
    }

    companion object {
        @Volatile
        private var instance: RuntimeGraph? = null

        fun from(context: Context): RuntimeGraph {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                instance ?: RuntimeGraph(context).also { instance = it }
            }
        }
    }
}
