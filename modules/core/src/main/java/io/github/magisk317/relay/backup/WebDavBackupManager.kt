package io.github.magisk317.relay.backup

import io.github.magisk317.xposed.logging.MagiskOtel
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.backup.webdav.WebDavClient
import io.github.magisk317.relay.backup.webdav.WebDavConfig
import io.github.magisk317.relay.data.backup.BackupManager
import io.github.magisk317.smscode.runtime.contract.backup.ExportResult
import io.github.magisk317.smscode.runtime.contract.backup.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WebDavBackupManager(
    private val context: Context,
) {
    private fun emitBackup(stage: String, statusOk: Boolean, reason: String, startedAt: Long) {
        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        MagiskOtel.event(
            name = if (stage.startsWith("restore")) "prefs.restore" else "prefs.backup",
            attributes = mapOf(
                "result" to if (statusOk) "ok" else "error",
                "duration_ms" to durationMs.toString(),
                "process" to "app",
                "stage" to stage,
                "reason" to reason,
                "source" to "webdav",
            ),
            statusOk = statusOk,
        )
    }
    private var config: WebDavConfig? = null
    private var client: WebDavClient? = null

    fun updateConfig(newConfig: WebDavConfig) {
        config = newConfig
        client = WebDavClient(newConfig)
    }

    fun getConfig(): WebDavConfig? = config

    suspend fun uploadBackup(): Result<CloudBackupMeta> = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        val currentClient = client ?: run {
            emitBackup(stage = "webdav_upload", statusOk = false, reason = "not_configured", startedAt = startedAt)
            return@withContext Result.failure(IllegalStateException("WebDAV not configured"))
        }
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
                val fileSize = tempFile.length()
                if (!tempFile.isFile || fileSize <= 0L) {
                    throw IllegalStateException("Local backup file missing or empty")
                }
                XLog.i("WebDAV backup export ready: name=%s size=%d", fileName, fileSize)

                currentClient.uploadFile(tempFile, fileName).getOrThrow()
                XLog.i("WebDAV backup upload success: name=%s size=%d", fileName, fileSize)

                CloudBackupMeta(
                    id = fileName,
                    name = fileName,
                    size = fileSize,
                    modifiedTime = timestamp,
                    source = BackupSource.WEBDAV,
                )
            } finally {
                if (tempFile.exists()) {
                    val deleted = tempFile.delete()
                    XLog.i("WebDAV backup temp cleanup: name=%s deleted=%s", fileName, deleted)
                }
            }
        }.also { result ->
            emitBackup(
                stage = "webdav_upload",
                statusOk = result.isSuccess,
                reason = if (result.isSuccess) "uploaded" else (result.exceptionOrNull()?.javaClass?.simpleName ?: "upload_failed"),
                startedAt = startedAt,
            )
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
                        source = BackupSource.WEBDAV,
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
        val startedAt = System.nanoTime()
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
        }.also { result ->
            emitBackup(
                stage = "webdav_restore",
                statusOk = result.isSuccess,
                reason = if (result.isSuccess) "restored" else (result.exceptionOrNull()?.javaClass?.simpleName ?: "restore_failed"),
                startedAt = startedAt,
            )
        }
    }

    suspend fun testConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        val currentClient = client ?: run {
            emitBackup(stage = "webdav_test", statusOk = false, reason = "not_configured", startedAt = startedAt)
            return@withContext Result.failure(IllegalStateException("WebDAV not configured"))
        }
        runCatching {
            currentClient.ensureDirectory().getOrThrow()
        }.also { result ->
            emitBackup(
                stage = "webdav_test",
                statusOk = result.isSuccess,
                reason = if (result.isSuccess) "connected" else (result.exceptionOrNull()?.javaClass?.simpleName ?: "test_failed"),
                startedAt = startedAt,
            )
        }
    }
}
