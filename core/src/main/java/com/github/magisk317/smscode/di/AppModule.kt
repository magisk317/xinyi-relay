package com.github.magisk317.smscode.di

import com.github.magisk317.smscode.data.db.AppDatabase
import com.github.magisk317.smscode.ui.home.AppConfigViewModel
import com.github.magisk317.smscode.ui.home.SettingsViewModel
import com.github.magisk317.smscode.ui.record.CodeRecordViewModel
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
