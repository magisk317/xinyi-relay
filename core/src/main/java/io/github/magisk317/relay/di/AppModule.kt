package io.github.magisk317.relay.di

import io.github.magisk317.relay.data.db.AppDatabase
import io.github.magisk317.relay.ui.home.AppConfigViewModel
import io.github.magisk317.relay.ui.home.SettingsViewModel
import io.github.magisk317.relay.ui.record.CodeRecordViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    // Database
    single { AppDatabase.getInstance(get()) }

    // ViewModels
    viewModelOf(::AppConfigViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::CodeRecordViewModel)
}
