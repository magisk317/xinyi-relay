package io.github.magisk317.relay.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.magisk317.relay.data.backup.BackupManager
import io.github.magisk317.smscode.runtime.common.backup.BackupImportResult
import io.github.magisk317.smscode.runtime.common.backup.BackupRule
import io.github.magisk317.smscode.runtime.common.backup.BackupSmsRecord
import io.github.magisk317.smscode.runtime.common.backup.ExportResult
import java.io.File

object RelayBackupManager {
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
