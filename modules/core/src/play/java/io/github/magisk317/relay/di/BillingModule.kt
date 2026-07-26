package io.github.magisk317.relay.di

import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.app.AppInitializer
import io.github.magisk317.relay.auth.AuthManager
import io.github.magisk317.relay.auth.FirebaseAuthManager
import io.github.magisk317.relay.auth.GoogleSignInHelper
import io.github.magisk317.relay.auth.GoogleSignInHelperImpl
import io.github.magisk317.relay.auth.NoOpAuthManager
import io.github.magisk317.relay.auth.NoOpGoogleSignInHelper
import io.github.magisk317.relay.backup.AutoCloudBackupCoordinator
import io.github.magisk317.relay.backup.CloudAutoBackupTrigger
import io.github.magisk317.relay.backup.CloudBackupProvider
import io.github.magisk317.relay.backup.GoogleDriveBackupManager
import io.github.magisk317.relay.backup.NoOpCloudBackupProvider
import io.github.magisk317.relay.backup.GoogleDriveCloudBackupProvider
import io.github.magisk317.relay.backup.WebDavBackupManager
import io.github.magisk317.relay.backup.WebDavCloudBackupProvider
import io.github.magisk317.uikit.billing.BillingInitializer
import io.github.magisk317.uikit.billing.BillingManager
import io.github.magisk317.relay.billing.BillingProvider
import io.github.magisk317.relay.billing.PlayBillingProvider
import io.github.magisk317.relay.billing.SubscriptionManager
import io.github.magisk317.relay.contract.backup.AutoBackupTrigger
import org.koin.dsl.bind
import org.koin.dsl.module

val billingModule = module {
    single { BillingManager(get()) }
    single { SubscriptionManager(get(), get(), get()) }
    single<BillingProvider> { PlayBillingProvider(get(), get()) }
    single { BillingInitializer(get()) } bind AppInitializer::class

    // Auth — Firebase 构造失败时降级为 NoOp
    single<GoogleSignInHelper> {
        runCatching {
            GoogleSignInHelperImpl(get())
        }.getOrElse { error ->
            XLog.e("GoogleSignInHelper init failed, degrading to NoOp", error)
            NoOpGoogleSignInHelper(get())
        }
    }
    single<AuthManager> {
        runCatching {
            FirebaseAuthManager(get(), get())
        }.getOrElse { error ->
            XLog.e("FirebaseAuthManager init failed, degrading to NoOp", error)
            NoOpAuthManager()
        }
    }

    // Google Drive backup — 构造失败时降级为 NoOp
    single { GoogleDriveBackupManager(get(), get()) }
    single<CloudBackupProvider> {
        runCatching {
            GoogleDriveCloudBackupProvider(get(), get(), get())
        }.getOrElse { error ->
            XLog.e("GoogleDriveCloudBackupProvider init failed, degrading to NoOp", error)
            NoOpCloudBackupProvider()
        }
    }

    // WebDAV backup
    single { WebDavBackupManager(get()) }
    single { WebDavCloudBackupProvider(get(), get()) }
    single { AutoCloudBackupCoordinator(get(), get<CloudBackupProvider>(), get()) }
    single<AutoBackupTrigger> { CloudAutoBackupTrigger(get()) }
}
