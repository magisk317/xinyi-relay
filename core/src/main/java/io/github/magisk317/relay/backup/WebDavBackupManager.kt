package io.github.magisk317.relay.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.magisk317.relay.backup.webdav.WebDavClient
import io.github.magisk317.relay.backup.webdav.WebDavConfig
import io.github.magisk317.relay.data.backup.BackupManager
import io.github.magisk317.smscode.runtime.contract.backup.ExportResult
import io.github.magisk317.smscode.runtime.contract.backup.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WebDavBackupManager(
    private val context: Context,
) {
    private var config: WebDavConfig? = null
    private var client: WebDavClient? = null

    fun updateConfig(newConfig: WebDavConfig) {
        config = newConfig
        client = WebDavClient(newConfig)
    }

    fun getConfig(): WebDavConfig? = config

    suspend fun uploadBackup(): Result<CloudBackupMeta> = withContext(Dispatchers.IO) {
        val currentClient = client ?: return@withContext Result.failure(IllegalStateException("WebDAV not configured"))
        runCatching {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "Relay-Cloud-$timestamp.zip"

            val tempFile = File(context.cacheDir, fileName)
            val tempUri = FileProvider.getUriForFile(context, "${context.packageName}.files", tempFile)
            try {
                val appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
                val exportResult = BackupManager.exportBackup(
                    context = context,
                    uri = tempUri,
                    ruleList = emptyList(),
                    preferences = null,
                    records = null,
                    appVersion = appVersion,
                    includeDatabase = true,
                )
                if (exportResult != ExportResult.SUCCESS) {
                    throw IllegalStateException("Local backup export failed: $exportResult")
                }

                currentClient.uploadFile(tempFile, fileName).getOrThrow()

                CloudBackupMeta(
                    id = fileName,
                    name = fileName,
                    size = tempFile.length(),
                    modifiedTime = timestamp,
                )
            } finally {
                tempFile.delete()
            }
        }
    }

    suspend fun listBackups(): Result<List<CloudBackupMeta>> = withContext(Dispatchers.IO) {
        val currentClient = client ?: return@withContext Result.failure(IllegalStateException("WebDAV not configured"))
        runCatching {
            val files = currentClient.listFiles().getOrThrow()
            files.filter { !it.isDirectory && it.name.endsWith(".zip") }
                .map { file ->
                    CloudBackupMeta(
                        id = file.name,
                        name = file.name,
                        size = file.size,
                        modifiedTime = file.lastModified,
                    )
                }
        }
    }

    suspend fun downloadBackup(fileName: String): Result<File> = withContext(Dispatchers.IO) {
        val currentClient = client ?: return@withContext Result.failure(IllegalStateException("WebDAV not configured"))
        runCatching {
            currentClient.downloadFile(fileName).getOrThrow()
        }
    }

    suspend fun deleteBackup(fileName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val currentClient = client ?: return@withContext Result.failure(IllegalStateException("WebDAV not configured"))
        runCatching {
            currentClient.deleteFile(fileName).getOrThrow()
        }
    }

    suspend fun restoreFromBackup(fileName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val tempFile = downloadBackup(fileName).getOrThrow()
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", tempFile)
                val appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
                val result = BackupManager.importRuleList(context, uri, appVersion)
                if (result.result != ImportResult.SUCCESS) {
                    throw IllegalStateException("Restore failed: ${result.result}")
                }
            } finally {
                tempFile.delete()
            }
        }
    }

    suspend fun testConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        val currentClient = client ?: return@withContext Result.failure(IllegalStateException("WebDAV not configured"))
        runCatching {
            currentClient.ensureDirectory().getOrThrow()
        }
    }
}
