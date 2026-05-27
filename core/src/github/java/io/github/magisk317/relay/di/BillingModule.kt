package io.github.magisk317.relay.di

import io.github.magisk317.relay.auth.AuthManager
import io.github.magisk317.relay.auth.GoogleSignInHelper
import io.github.magisk317.relay.auth.NoOpAuthManager
import io.github.magisk317.relay.auth.NoOpGoogleSignInHelper
import io.github.magisk317.relay.backup.CloudBackupProvider
import io.github.magisk317.relay.backup.GithubCloudBackupProvider
import io.github.magisk317.relay.backup.GoogleDriveBackupManager
import io.github.magisk317.relay.backup.WebDavBackupManager
import io.github.magisk317.relay.backup.WebDavCloudBackupProvider
import io.github.magisk317.relay.billing.BillingProvider
import io.github.magisk317.relay.billing.NoOpBillingProvider
import org.koin.dsl.module

val billingModule = module {
    single<BillingProvider> { NoOpBillingProvider() }

    // Google Drive backup
    single { GoogleDriveBackupManager(get(), get()) }
    single<CloudBackupProvider> { GithubCloudBackupProvider(get(), get()) }

    // WebDAV backup
    single { WebDavBackupManager(get()) }
    single { WebDavCloudBackupProvider(get()) }

    single<GoogleSignInHelper> { NoOpGoogleSignInHelper(get()) }
    single<AuthManager> { NoOpAuthManager() }
}
