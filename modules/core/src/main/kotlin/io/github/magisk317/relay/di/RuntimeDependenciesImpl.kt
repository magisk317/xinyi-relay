package io.github.magisk317.relay.di

import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.bootstrap.RuntimeDependencies
import io.github.magisk317.relay.contract.backup.AutoBackupTrigger
import io.github.magisk317.relay.contract.repository.ConfigSyncCoordinator
import io.github.magisk317.relay.contract.repository.LocalConfigRepository
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.domain.pipeline.EventPipeline
import io.github.magisk317.relay.domain.system.RuntimeRecordFacade
import io.github.magisk317.relay.engine.service.AppConfigRepository
import io.github.magisk317.relay.engine.service.MessageRecordRepository
import io.github.magisk317.relay.engine.service.ScheduledTaskRepository
import org.koin.core.Koin
import org.koin.core.qualifier.named

/**
 * Koin-backed implementation of [RuntimeDependencies].
 *
 * Registered once in [io.github.magisk317.relay.ui.app.SmsCodeApplication.onCreate]
 * right after `startKoin`, bridging the Koin container to `:runtime` callers
 * without requiring `:runtime` to depend on Koin directly.
 */
class RuntimeDependenciesImpl(private val koin: Koin) : RuntimeDependencies {
    override val database: AppDatabase get() = koin.get()
    override val preferenceDataSource: PreferenceDataSource get() = koin.get()
    override val settingsRepository: SettingsPreferencesRepository get() = koin.get()
    override val relayRecordRepository: MessageRecordRepository get() = koin.get()
    override val localConfigRepository: LocalConfigRepository get() = koin.get()
    override val configSyncCoordinator: ConfigSyncCoordinator get() = koin.get()
    override val runtimeRecordFacade: RuntimeRecordFacade get() = koin.get()
    override val configRepository: AppConfigRepository get() = koin.get()
    override val autoBackupTrigger: AutoBackupTrigger get() = koin.get()
    override val eventPipeline: EventPipeline get() = koin.get()
    override val scheduledTaskRepository: ScheduledTaskRepository get() = koin.get()
}
