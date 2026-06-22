package io.github.magisk317.relay.bootstrap

import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.contract.backup.AutoBackupTrigger
import io.github.magisk317.relay.contract.repository.RemoteSyncRepository
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.domain.pipeline.EventPipeline
import io.github.magisk317.relay.domain.system.RuntimeRecordFacade
import io.github.magisk317.relay.engine.service.AppConfigRepository
import io.github.magisk317.relay.engine.service.MessageRecordRepository
import io.github.magisk317.relay.engine.service.ScheduledTaskRepository

/**
 * Service-locator interface for runtime singletons.
 *
 * `:runtime` callers use [get] to resolve dependencies without importing
 * Koin directly. The single implementation is registered once in
 * `SmsCodeApplication.onCreate()` after `startKoin`.
 *
 * Only the properties actually consumed by `:runtime` code are listed here.
 * `RuntimeGraph` (in `:core`) exposes the full set for `:core` callers.
 */
interface RuntimeDependencies {

    val database: AppDatabase
    val preferenceDataSource: PreferenceDataSource
    val settingsRepository: SettingsPreferencesRepository
    val relayRecordRepository: MessageRecordRepository
    val remoteAgentRepository: RemoteSyncRepository
    val runtimeRecordFacade: RuntimeRecordFacade
    val configRepository: AppConfigRepository
    val autoBackupTrigger: AutoBackupTrigger
    val eventPipeline: EventPipeline
    val scheduledTaskRepository: ScheduledTaskRepository

    companion object {
        @Volatile
        private var instance: RuntimeDependencies? = null

        /**
         * Register the application-wide implementation. Must be called once
         * in `SmsCodeApplication.onCreate()` right after `startKoin`.
         */
        fun register(deps: RuntimeDependencies) {
            instance = deps
        }

        /**
         * Resolve the registered implementation. Throws if [register] has
         * not been called yet (i.e. before `startKoin`).
         */
        fun get(): RuntimeDependencies =
            instance ?: error(
                "RuntimeDependencies not registered; call register() in SmsCodeApplication.onCreate() after startKoin"
            )
    }
}
