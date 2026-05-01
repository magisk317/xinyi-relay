package io.github.magisk317.relay.data.backup

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.platform.sender.SenderSettingSanitizer
import io.github.magisk317.relay.data.db.AppDatabase
import io.github.magisk317.relay.runtime.BuildConfig
import io.github.magisk317.smscode.runtime.common.backup.BackupDatabaseHooks
import io.github.magisk317.smscode.runtime.common.backup.BackupImportResult
import io.github.magisk317.smscode.runtime.common.backup.BackupManagerConfig
import io.github.magisk317.smscode.runtime.common.backup.BackupManagerCore
import io.github.magisk317.smscode.runtime.common.backup.BackupRule
import io.github.magisk317.smscode.runtime.common.backup.BackupSmsRecord
import io.github.magisk317.smscode.runtime.common.backup.ExportResult
import io.github.magisk317.smscode.runtime.common.backup.ImportResult
import java.io.BufferedInputStream
import java.io.File
import java.io.PushbackInputStream
import java.util.zip.ZipInputStream

object BackupManager {
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

    private val config = BackupManagerConfig(
        backupDirectoryName = "Relay",
        backupFileNamePrefix = "Relay-",
        databaseFileName = "relay_room.db",
        legacyDatabaseFileNames = listOf("xrelay_room.db", "xsmscode_room.db"),
        fileProviderAuthority = "${BuildConfig.APPLICATION_ID}.files",
    )

    private val databaseHooks = BackupDatabaseHooks(
        beforeDatabaseSnapshot = { context ->
            runCatching {
                AppDatabase.getInstance(context).openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")
            }
        },
        beforeDatabaseRestore = {
            AppDatabase.closeInstance()
        },
    )

    @JvmStatic
    fun getBackupDir(context: Context): File = BackupManagerCore.getBackupDir(context, config)

    @JvmStatic
    fun getBackupFileExtension(): String = BackupManagerCore.getBackupFileExtension()

    @JvmStatic
    fun getDefaultBackupFilename(context: Context, includeDatabase: Boolean = false): String =
        BackupManagerCore.getDefaultBackupFilename(context, config, includeDatabase)

    @JvmStatic
    fun getBackupFiles(context: Context): Array<File>? = BackupManagerCore.getBackupFiles(context, config)

    @JvmStatic
    fun exportRuleList(file: File, ruleList: List<BackupRule>, appVersion: String): ExportResult =
        BackupManagerCore.exportRuleList(file, ruleList, appVersion)

    @JvmStatic
    fun exportBackup(
        context: Context,
        uri: Uri,
        ruleList: List<BackupRule>,
        preferences: Map<String, String?>?,
        records: List<BackupSmsRecord>?,
        appVersion: String,
        includeDatabase: Boolean = false,
    ): ExportResult = BackupManagerCore.exportBackup(
        context = context,
        uri = uri,
        ruleList = ruleList,
        preferences = preferences,
        records = records,
        appVersion = appVersion,
        config = config,
        hooks = databaseHooks,
        includeDatabase = includeDatabase,
    )

    @JvmStatic
    fun exportRuleList(context: Context, uri: Uri, ruleList: List<BackupRule>, appVersion: String): ExportResult =
        BackupManagerCore.exportRuleList(context, uri, ruleList, appVersion, config)

    @JvmStatic
    fun getExportRuleListSAFIntent(context: Context, includeDatabase: Boolean = false): Intent =
        BackupManagerCore.getExportRuleListSAFIntent(context, config, includeDatabase)

    @JvmStatic
    fun importRuleList(context: Context, uri: Uri, currentAppVersion: String): BackupImportResult =
        BackupManagerCore.importRuleList(context, uri, currentAppVersion)

    @JvmStatic
    fun getImportRuleListSAFIntent(context: Context): Intent =
        BackupManagerCore.getImportRuleListSAFIntent(context, config)

    @JvmStatic
    fun restoreDatabaseFromBackup(context: Context, uri: Uri): Boolean =
        BackupManagerCore.restoreDatabaseFromBackup(context, uri, config, databaseHooks)

    @JvmStatic
    fun inspectBackup(context: Context, uri: Uri): BackupInspection {
        val importResult = runCatching { BackupManagerCore.importRuleList(context, uri, resolveAppVersion(context)) }.getOrNull()
        val dbInspection = inspectBackupDatabase(context, uri)
        return BackupInspection(
            payloadReadable = importResult?.result == ImportResult.SUCCESS,
            payloadRules = importResult?.rules?.size ?: 0,
            payloadPreferences = importResult?.preferences?.size ?: 0,
            payloadRecords = importResult?.records?.size ?: 0,
            databasePresent = dbInspection?.databasePresent == true,
            senderCount = dbInspection?.senderCount ?: 0,
            blankSenderConfigs = dbInspection?.blankSenderConfigs ?: 0,
            degradedSenderConfigs = dbInspection?.degradedSenderConfigs ?: 0,
        )
    }

    @JvmStatic
    fun shareBackupFile(context: Context, file: File) {
        BackupManagerCore.shareBackupFile(context, file, config)
    }

    private data class BackupDatabaseInspection(
        val databasePresent: Boolean,
        val senderCount: Int,
        val blankSenderConfigs: Int,
        val degradedSenderConfigs: Int,
    )

    private fun inspectBackupDatabase(context: Context, uri: Uri): BackupDatabaseInspection? {
        context.contentResolver.openInputStream(uri)?.use { raw ->
            val pb = PushbackInputStream(BufferedInputStream(raw), 4)
            val header = ByteArray(4)
            val readCount = pb.read(header)
            if (readCount > 0) pb.unread(header, 0, readCount)
            val isZip = readCount == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
            if (!isZip) return null

            val tmpDir = File(context.cacheDir, "backup_inspect_tmp").apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }
            try {
                var foundMainDb = false
                ZipInputStream(pb).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val baseName = entry.name.substringAfterLast('/').substringAfterLast('\\')
                            val normalized = normalizeBackupDbName(baseName)
                            if (normalized != null) {
                                val outFile = File(tmpDir, normalized)
                                outFile.outputStream().use { output -> zis.copyTo(output) }
                                if (normalized == config.databaseFileName) foundMainDb = true
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
                if (!foundMainDb) return BackupDatabaseInspection(false, 0, 0, 0)
                return inspectSenderConfigsFromDatabase(File(tmpDir, config.databaseFileName))
            } finally {
                runCatching { tmpDir.deleteRecursively() }
            }
        }
        return null
    }

    private fun normalizeBackupDbName(baseName: String): String? = when {
        baseName == config.databaseFileName -> config.databaseFileName
        baseName == "${config.databaseFileName}-wal" -> "${config.databaseFileName}-wal"
        baseName == "${config.databaseFileName}-shm" -> "${config.databaseFileName}-shm"
        config.legacyDatabaseFileNames.any { it == baseName } -> config.databaseFileName
        config.legacyDatabaseFileNames.any { "$it-wal" == baseName } -> "${config.databaseFileName}-wal"
        config.legacyDatabaseFileNames.any { "$it-shm" == baseName } -> "${config.databaseFileName}-shm"
        else -> null
    }

    private fun inspectSenderConfigsFromDatabase(databaseFile: File): BackupDatabaseInspection {
        if (!databaseFile.exists()) return BackupDatabaseInspection(false, 0, 0, 0)

        var senderCount = 0
        var blankSenderConfigs = 0
        var degradedSenderConfigs = 0
        val defaultJsonCache = hashMapOf<Int, String>()

        SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT id, type, name, json_setting FROM Sender", null).use { cursor ->
                while (cursor.moveToNext()) {
                    senderCount += 1
                    val type = cursor.getInt(1)
                    val name = cursor.getString(2).orEmpty()
                    val jsonSetting = cursor.getString(3).orEmpty()
                    val trimmed = jsonSetting.trim()
                    if (trimmed.isBlank()) {
                        blankSenderConfigs += 1
                        continue
                    }
                    val defaultJson = defaultJsonCache.getOrPut(type) {
                        SenderSettingSanitizer.sanitizeJsonLenient(type, "")
                    }
                    val sanitized = SenderSettingSanitizer.sanitizeJsonLenient(type, jsonSetting)
                    val rawCanonical = canonicalizeJson(jsonSetting)
                    if (sanitized == defaultJson && rawCanonical != defaultJson) {
                        degradedSenderConfigs += 1
                        XLog.w(
                            "Backup inspect sender degraded to defaults: type=%d name=%s raw=%s",
                            type,
                            name,
                            trimmed.take(256),
                        )
                    }
                }
            }
        }
        return BackupDatabaseInspection(true, senderCount, blankSenderConfigs, degradedSenderConfigs)
    }

    private fun canonicalizeJson(raw: String): String {
        return SenderSettingSanitizer.sanitizeJsonLenient(0, raw).takeIf { raw.trim().isNotBlank() } ?: raw.trim()
    }

    private fun resolveAppVersion(context: Context): String {
        val pm = context.packageManager
        return runCatching {
            val pkgInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            pkgInfo.versionName.orEmpty()
        }.getOrDefault("")
    }
}
