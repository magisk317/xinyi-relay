package io.github.magisk317.relay.bootstrap

import android.content.Context
import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.contract.repository.RemoteSyncRepository
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
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
import io.github.magisk317.relay.engine.service.SystemInfoProvider
import org.koin.core.context.GlobalContext

/**
 * Typed facade over the single application object graph.
 *
 * The graph itself is constructed exactly once in the Koin `coreModule`; this
 * class merely resolves each member from the running Koin container, giving
 * non-UI code (services, receivers, initializers) a static, context-based entry
 * point (`RuntimeGraph.from(context).x`) without a second hand-maintained
 * construction list.
 *
 * Every `from(...)` call site runs after `startKoin` (single process; Koin is
 * started first in `SmsCodeApplication.onCreate`, and no ContentProvider or
 * pre-onCreate code touches this facade).
 */
object RuntimeGraph {

    private val koin get() = GlobalContext.get()

    val database: AppDatabase get() = koin.get()
    val preferenceDataSource: PreferenceDataSource get() = koin.get()
    val settingsRepository: SettingsPreferencesRepository get() = koin.get()
    val relayRecordRepository: MessageRecordRepository get() = koin.get()
    val analyticsRepository: RuntimeAnalyticsProvider get() = koin.get()
    val remoteAgentRepository: RemoteSyncRepository get() = koin.get()
    val runtimeRecordFacade: RuntimeRecordFacade get() = koin.get()
    val configRepository: AppConfigRepository get() = koin.get()
    val scheduledTaskRepository: ScheduledTaskRepository get() = koin.get()
    val systemInfoProvider: SystemInfoProvider get() = koin.get()
    val messageFormatter: MessageFormatter get() = koin.get()
    val eventGatekeeper: EventGatekeeper get() = koin.get()
    val routingResolver: RoutingResolver get() = koin.get()
    val senderSelector: SenderSelector get() = koin.get()
    val dispatchExecutor: DispatchExecutor get() = koin.get()
    val dispatchResultWriter: DispatchResultWriter get() = koin.get()
    val eventPipeline: EventPipeline get() = koin.get()

    /**
     * Static entry point for lifecycle-less, non-UI code (services, receivers,
     * initializers) that has no Koin scope to inject into. The [context] is no
     * longer needed (the graph lives in the global Koin container) but is kept
     * so the ~38 existing call sites compile unchanged.
     */
    @Suppress("UNUSED_PARAMETER")
    fun from(context: Context): RuntimeGraph = this
}
