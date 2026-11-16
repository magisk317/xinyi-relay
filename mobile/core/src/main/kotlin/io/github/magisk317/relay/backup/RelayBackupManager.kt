package io.github.magisk317.relay.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.magisk317.relay.data.backup.BackupManager
import io.github.magisk317.smscode.runtime.contract.backup.BackupImportResult
import io.github.magisk317.smscode.runtime.contract.backup.BackupRule
import io.github.magisk317.smscode.runtime.contract.backup.BackupSmsRecord
import io.github.magisk317.smscode.runtime.contract.backup.ExportResult
import io.github.magisk317.smscode.runtime.contract.backup.ImportResult
import io.github.magisk317.xposed.backup.BackupPayloadSource
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Product-data backup facade shared by the SAF-driven UI and the cloud
 * transports. Also implements the kit's [BackupPayloadSource] seam so the
 * transport-agnostic stack can produce and restore the app's backup archive
 * without knowing its format.
 */
object RelayBackupManager : BackupPayloadSource {

    // Matches the file names produceBackup writes; the WebDAV transport uploads
    // them verbatim and scopes remote listings to this prefix.
    override val archivePrefix: String = "Relay-Cloud-"
    data class BackupInspection(
        val payloadReadable: Boolean,
        val payloadRules: Int,
        val payloadPreferences: Int,
        val payloadRecords: Int,
        val databasePresent: Boolean,
        val senderCount: Int,
        val blankSenderConfigs: Int,
        val degradedSenderConfigs: Int,
    ) {
        fun toLogString(): String {
            return "payloadReadable=$payloadReadable rules=$payloadRules prefs=$payloadPreferences " +
                "records=$payloadRecords databasePresent=$databasePresent senders=$senderCount " +
                "blankSenderConfigs=$blankSenderConfigs degradedSenderConfigs=$degradedSenderConfigs"
        }
    }

    fun getBackupDir(context: Context): File = BackupManager.getBackupDir(context)

    fun getBackupFileExtension(): String = BackupManager.getBackupFileExtension()

    fun getDefaultBackupFilename(context: Context, includeDatabase: Boolean = false): String =
        BackupManager.getDefaultBackupFilename(context, includeDatabase)

    fun getBackupFiles(context: Context): Array<File>? = BackupManager.getBackupFiles(context)

    fun exportBackup(
        context: Context,
        uri: Uri,
        ruleList: List<BackupRule>,
        preferences: Map<String, String?>?,
        records: List<BackupSmsRecord>?,
        appVersion: String,
        includeDatabase: Boolean = false,
    ): ExportResult = BackupManager.exportBackup(
        context = context,
        uri = uri,
        ruleList = ruleList,
        preferences = preferences,
        records = records,
        appVersion = appVersion,
        includeDatabase = includeDatabase,
    )

    fun getExportRuleListSAFIntent(context: Context, includeDatabase: Boolean = false): Intent =
        BackupManager.getExportRuleListSAFIntent(context, includeDatabase)

    fun importRuleList(context: Context, uri: Uri, currentAppVersion: String): BackupImportResult =
        BackupManager.importRuleList(context, uri, currentAppVersion)

    fun getImportRuleListSAFIntent(context: Context): Intent =
        BackupManager.getImportRuleListSAFIntent(context)

    fun restoreDatabaseFromBackup(context: Context, uri: Uri): Boolean =
        BackupManager.restoreDatabaseFromBackup(context, uri)

    fun inspectBackup(context: Context, uri: Uri): BackupInspection {
        return BackupManager.inspectBackup(context, uri).toCoreInspection()
    }

    fun shareBackupFile(context: Context, file: File) {
        BackupManager.shareBackupFile(context, file)
    }

    override suspend fun produceBackup(context: Context): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "Relay-Cloud-$timestamp.zip"
        val tempFile = File(context.cacheDir, fileName)
        val tempUri = FileProvider.getUriForFile(context, "${context.packageName}.files", tempFile)
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
        return tempFile
    }

    override suspend fun applyRestore(context: Context, backup: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", backup)
        val appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        val result = BackupManager.importRuleList(context, uri, appVersion)
        if (result.result != ImportResult.SUCCESS) {
            throw IllegalStateException("Restore failed: ${result.result}")
        }
    }

    private fun BackupManager.BackupInspection.toCoreInspection(): BackupInspection {
        return BackupInspection(
            payloadReadable = payloadReadable,
            payloadRules = payloadRules,
            payloadPreferences = payloadPreferences,
            payloadRecords = payloadRecords,
            databasePresent = databasePresent,
            senderCount = senderCount,
            blankSenderConfigs = blankSenderConfigs,
            degradedSenderConfigs = degradedSenderConfigs,
        )
    }
}
