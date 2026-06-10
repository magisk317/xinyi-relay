package io.github.magisk317.relay.di

import io.github.magisk317.relay.app.AppInitializer
import io.github.magisk317.relay.auth.AuthManager
import io.github.magisk317.relay.auth.FirebaseAuthManager
import io.github.magisk317.relay.auth.GoogleSignInHelper
import io.github.magisk317.relay.auth.GoogleSignInHelperImpl
import io.github.magisk317.relay.backup.AutoCloudBackupCoordinator
import io.github.magisk317.relay.backup.CloudAutoBackupTrigger
import io.github.magisk317.relay.backup.CloudBackupProvider
import io.github.magisk317.relay.backup.GoogleDriveBackupManager
import io.github.magisk317.relay.backup.PlayCloudBackupProvider
import io.github.magisk317.relay.backup.WebDavBackupManager
import io.github.magisk317.relay.backup.WebDavCloudBackupProvider
import io.github.magisk317.relay.billing.BillingInitializer
import io.github.magisk317.relay.billing.BillingManager
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

    // Google Drive backup
    single { GoogleDriveBackupManager(get(), get()) }
    single<CloudBackupProvider> { PlayCloudBackupProvider(get(), get(), get()) }

    // WebDAV backup
    single { WebDavBackupManager(get()) }
    single { WebDavCloudBackupProvider(get(), get()) }
    single { AutoCloudBackupCoordinator(get(), get<CloudBackupProvider>(), get()) }
    single<AutoBackupTrigger> { CloudAutoBackupTrigger(get()) }

    single<GoogleSignInHelper> { GoogleSignInHelperImpl(get()) }
    single { FirebaseAuthManager(get(), get()) }
    single<AuthManager> { get<FirebaseAuthManager>() }
}
