package io.github.magisk317.relay.backup

import android.content.Context
import io.github.magisk317.relay.backup.drive.GoogleDriveBackupConfigStore
import io.github.magisk317.xposed.backup.BackupSource
import io.github.magisk317.xposed.backup.CloudBackupProvider
import io.github.magisk317.xposed.backup.CloudBackupProviderResolution
import io.github.magisk317.xposed.backup.CloudBackupProviderResolver
import io.github.magisk317.xposed.backup.webdav.WebDavCloudBackupProvider
import io.github.magisk317.xposed.backup.webdav.WebDavConfigStore

/**
 * Relay wiring of the kit's provider-resolver seam: refreshes the persisted
 * per-source configuration right before an auto backup runs and hands the kit
 * coordinator a ready provider. Mirrors the former inline GOOGLE_DRIVE/WEBDAV
 * branch of the duplicated coordinator.
 */
class RelayCloudBackupProviderResolver(
    private val googleDriveProvider: CloudBackupProvider,
    private val webDavProvider: WebDavCloudBackupProvider,
) : CloudBackupProviderResolver {
    override suspend fun resolve(
        context: Context,
        source: BackupSource,
    ): CloudBackupProviderResolution {
        return when (source) {
            BackupSource.GOOGLE_DRIVE -> {
                val config = GoogleDriveBackupConfigStore.getConfig(context)
                (googleDriveProvider as? GoogleDriveConfigurableBackupProvider)
                    ?.updateGoogleDriveConfig(config)
                CloudBackupProviderResolution.Ready(googleDriveProvider)
            }

            BackupSource.WEBDAV -> {
                val config = WebDavConfigStore.getConfig(context)
                if (config == null) {
                    CloudBackupProviderResolution.Skipped("webdav_config_missing")
                } else {
                    webDavProvider.updateConfig(config)
                    CloudBackupProviderResolution.Ready(webDavProvider)
                }
            }
        }
    }
}
