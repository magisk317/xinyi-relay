package io.github.magisk317.relay.di

import io.github.magisk317.relay.auth.AuthManager
import io.github.magisk317.relay.auth.GoogleSignInHelper
import io.github.magisk317.relay.auth.NoOpAuthManager
import io.github.magisk317.relay.auth.NoOpGoogleSignInHelper
import io.github.magisk317.relay.backup.GoogleDriveBackupManager
import io.github.magisk317.relay.backup.GoogleDriveCloudBackupProvider
import io.github.magisk317.relay.backup.RelayBackupManager
import io.github.magisk317.relay.backup.RelayCloudBackupProviderResolver
import io.github.magisk317.relay.billing.BillingProvider
import io.github.magisk317.relay.billing.NoOpBillingProvider
import io.github.magisk317.xposed.backup.AutoBackupTrigger
import io.github.magisk317.xposed.backup.AutoCloudBackupCoordinator
import io.github.magisk317.xposed.backup.CloudAutoBackupTrigger
import io.github.magisk317.xposed.backup.CloudBackupProvider
import io.github.magisk317.xposed.backup.webdav.WebDavBackupManager
import io.github.magisk317.xposed.backup.webdav.WebDavCloudBackupProvider
import org.koin.dsl.module

val billingModule = module {
    single<BillingProvider> { NoOpBillingProvider() }

    // Google Drive backup
    single { GoogleDriveBackupManager(get(), get()) }
    single<CloudBackupProvider> { GoogleDriveCloudBackupProvider(get(), get(), get()) }

    // WebDAV backup
    single { WebDavBackupManager(get(), RelayBackupManager) }
    single { WebDavCloudBackupProvider(get(), get()) }
    single { RelayCloudBackupProviderResolver(get<CloudBackupProvider>(), get()) }
    single { AutoCloudBackupCoordinator(get(), get()) }
    single<AutoBackupTrigger> { CloudAutoBackupTrigger(get()) }

    single<GoogleSignInHelper> { NoOpGoogleSignInHelper(get()) }
    single<AuthManager> { NoOpAuthManager() }
}
