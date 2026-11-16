package io.github.magisk317.relay.di

import io.github.magisk317.relay.app.AppInitializer
import io.github.magisk317.relay.auth.AuthManager
import io.github.magisk317.relay.auth.FirebaseAuthManager
import io.github.magisk317.relay.auth.GoogleSignInHelper
import io.github.magisk317.relay.auth.GoogleSignInHelperImpl
import io.github.magisk317.relay.auth.NoOpAuthManager
import io.github.magisk317.relay.auth.NoOpGoogleSignInHelper
import io.github.magisk317.relay.backup.GoogleDriveBackupManager
import io.github.magisk317.relay.backup.GoogleDriveCloudBackupProvider
import io.github.magisk317.relay.backup.RelayBackupManager
import io.github.magisk317.relay.backup.RelayCloudBackupProviderResolver
import io.github.magisk317.relay.billing.BillingProvider
import io.github.magisk317.relay.billing.PlayBillingProvider
import io.github.magisk317.uikit.billing.BillingInitializer
import io.github.magisk317.uikit.billing.BillingManager
import io.github.magisk317.xposed.backup.AutoBackupTrigger
import io.github.magisk317.xposed.backup.AutoCloudBackupCoordinator
import io.github.magisk317.xposed.backup.CloudAutoBackupTrigger
import io.github.magisk317.xposed.backup.CloudBackupProvider
import io.github.magisk317.xposed.backup.NoOpCloudBackupProvider
import io.github.magisk317.xposed.backup.webdav.WebDavBackupManager
import io.github.magisk317.xposed.backup.webdav.WebDavCloudBackupProvider
import io.github.magisk317.xposed.logging.XLog
import org.koin.dsl.bind
import org.koin.dsl.module

val billingModule = module {
    single { BillingManager(get()) }
    single<BillingProvider> { PlayBillingProvider(get()) }
    single { BillingInitializer(get()) } bind AppInitializer::class

    // Auth — Firebase 构造失败时降级为 NoOp
    single<GoogleSignInHelper> {
        runCatching {
            GoogleSignInHelperImpl(get())
        }.getOrElse { error ->
            XLog.e(message = "GoogleSignInHelper init failed, degrading to NoOp", error)
            NoOpGoogleSignInHelper(get())
        }
    }
    single<AuthManager> {
        runCatching {
            FirebaseAuthManager(get(), get())
        }.getOrElse { error ->
            XLog.e(message = "FirebaseAuthManager init failed, degrading to NoOp", error)
            NoOpAuthManager()
        }
    }

    // Google Drive backup — 构造失败时降级为 NoOp
    single { GoogleDriveBackupManager(get(), get()) }
    single<CloudBackupProvider> {
        runCatching {
            GoogleDriveCloudBackupProvider(get(), get(), get())
        }.getOrElse { error ->
            XLog.e(message = "GoogleDriveCloudBackupProvider init failed, degrading to NoOp", error)
            NoOpCloudBackupProvider()
        }
    }

    // WebDAV backup
    single { WebDavBackupManager(get(), RelayBackupManager) }
    single { WebDavCloudBackupProvider(get(), get()) }
    single { RelayCloudBackupProviderResolver(get<CloudBackupProvider>(), get()) }
    single { AutoCloudBackupCoordinator(get(), get()) }
    single<AutoBackupTrigger> { CloudAutoBackupTrigger(get()) }
}
