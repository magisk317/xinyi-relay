package com.github.magisk317.smscode.feature.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.github.magisk317.smscode.common.utils.JsonUtils
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.data.db.AppDatabase
import com.github.magisk317.smscode.data.db.DBManager
import com.github.magisk317.smscode.feature.backup.exception.BackupInvalidException
import com.github.magisk317.smscode.feature.backup.exception.VersionInvalidException
import com.github.magisk317.smscode.feature.backup.exception.VersionMissedException
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.PushbackInputStream
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {

    private const val BACKUP_DIRECTORY = "SmsCode"
    private const val BACKUP_FILE_EXTENSION = ".scebak"
    private const val BACKUP_ZIP_EXTENSION = ".zip"
    private const val BACKUP_FILE_NAME_PREFIX = "SmsCode-"

    private const val BACKUP_MIME_TYPE = "application/json"
    private const val BACKUP_ZIP_MIME_TYPE = "application/zip"
    private const val BACKUP_PAYLOAD_ENTRY = "backup.scebak"
    private const val DB_FILE_NAME = "xsmscode_room.db"

    @JvmStatic
    fun getBackupDir(context: Context): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        return File(baseDir, BACKUP_DIRECTORY)
    }

    @JvmStatic
    fun getBackupFileExtension(): String = BACKUP_FILE_EXTENSION

    @JvmStatic
    fun getDefaultBackupFilename(context: Context, includeDatabase: Boolean = false): String {
        val sdf = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())
        val dateStr = sdf.format(Date())
        val backupDir = getBackupDir(context)
        val suffix = if (includeDatabase) "-db" else ""
        val basename = "${BACKUP_FILE_NAME_PREFIX}${dateStr}-schema${BackupConst.BACKUP_VERSION}$suffix"
        val extension = if (includeDatabase) BACKUP_ZIP_EXTENSION else BACKUP_FILE_EXTENSION
        var filename = basename + extension
        var i = 2
        while (File(backupDir, filename).exists()) {
            filename = "$basename-$i$extension"
            i++
        }
        return filename
    }

    @JvmStatic
    fun getBackupFiles(context: Context): Array<File>? {
        val backupDir = getBackupDir(context)
        if (!backupDir.exists()) return null
        val files = backupDir.listFiles { _, name -> name.endsWith(BACKUP_FILE_EXTENSION) }

        if (files != null) {
            Arrays.sort(files) { f1: File, f2: File ->
                val s1 = f1.name
                val s2 = f2.name
                val extLength = BACKUP_FILE_EXTENSION.length
                val n1 = s1.substring(0, s1.length - extLength)
                val n2 = s2.substring(0, s2.length - extLength)
                n1.compareTo(n2)
            }
        }
        return files
    }

    @JvmStatic
    fun exportRuleList(file: File, ruleList: List<BackupRule>, appVersion: String): ExportResult {
        val parentFile = file.parentFile
        if (parentFile != null && !parentFile.exists()) {
            parentFile.mkdirs()
        }

        try {
            RuleExporter(file).use { exporter ->
                exporter.doExport(ruleList, appVersion)
                return ExportResult.SUCCESS
            }
        } catch (e: IOException) {
            XLog.e("Export SmsCode rules failed", e)
            return ExportResult.FAILED
        }
    }

    @JvmStatic
    fun exportBackup(
        context: Context,
        uri: Uri,
        ruleList: List<BackupRule>,
        preferences: Map<String, String?>?,
        records: List<BackupSmsRecord>?,
        appVersion: String,
        includeDatabase: Boolean = false,
    ): ExportResult {
        XLog.i(
            "exportBackup start: uri=%s rules=%d prefs=%d records=%d appVersion=%s includeDatabase=%s",
            uri.toString(),
            ruleList.size,
            preferences?.size ?: 0,
            records?.size ?: 0,
            appVersion,
            includeDatabase,
        )
        if (includeDatabase) {
            try {
                exportBackupZipWithDatabase(context, uri, ruleList, preferences, records, appVersion)
                XLog.i("exportBackup success (zip with database)")
                return ExportResult.SUCCESS
            } catch (e: IOException) {
                XLog.e("Export SmsCode backup(zip) failed", e)
                return ExportResult.FAILED
            } catch (e: IllegalStateException) {
                XLog.e("Export SmsCode backup(zip) failed", e)
                return ExportResult.FAILED
            }
        }
        try {
            RuleExporter(context.contentResolver.openOutputStream(uri)).use { exporter ->
                exporter.doExport(ruleList, appVersion, preferences, records)
                XLog.i("exportBackup success")
                return ExportResult.SUCCESS
            }
        } catch (e: IOException) {
            XLog.e("Export SmsCode backup failed", e)
            return ExportResult.FAILED
        }
    }

    @JvmStatic
    fun exportRuleList(context: Context, uri: Uri, ruleList: List<BackupRule>, appVersion: String): ExportResult =
        exportBackup(context, uri, ruleList, null, null, appVersion)

    /**
     * 获取导出规则列表的 SAF (Storage Access Framework) 的 Intent
     */
    @JvmStatic
    fun getExportRuleListSAFIntent(context: Context, includeDatabase: Boolean = false): Intent {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = if (includeDatabase) BACKUP_ZIP_MIME_TYPE else BACKUP_MIME_TYPE
        intent.putExtra(Intent.EXTRA_TITLE, getDefaultBackupFilename(context, includeDatabase))

        return intent
    }

    @JvmStatic
    fun importRuleList(context: Context, uri: Uri, currentAppVersion: String): BackupImportResult {
        var ruleImporter: RuleImporter? = null
        try {
            XLog.i("importRuleList start: uri=%s", uri.toString())
            val payloadBytes = readPayloadBytes(context, uri)
                ?: return BackupImportResult(ImportResult.READ_FAILED)
            ruleImporter = RuleImporter(ByteArrayInputStream(payloadBytes))
            val payload = ruleImporter.parsePayload()
            val schemaVersion = payload.schemaVersion
            XLog.i(
                "importRuleList parsed: schema=%d appVersion=%s rules=%d prefs=%d records=%d",
                schemaVersion,
                payload.appVersion,
                payload.rules.size,
                payload.preferences?.size ?: 0,
                payload.records?.size ?: 0,
            )
            if (schemaVersion > BackupConst.BACKUP_VERSION) {
                return BackupImportResult(ImportResult.VERSION_TOO_NEW)
            }
            if (schemaVersion < BackupConst.BACKUP_VERSION) {
                return BackupImportResult(ImportResult.VERSION_TOO_OLD)
            }
            val warning = resolveWarning(payload.appVersion, currentAppVersion)
            return BackupImportResult(
                ImportResult.SUCCESS,
                payload.rules,
                payload.preferences,
                payload.records,
                warning,
            )
        } catch (e: IOException) {
            XLog.e("Error occurs in importRuleList", e)
            return BackupImportResult(ImportResult.READ_FAILED)
        } catch (e: VersionMissedException) {
            XLog.e("Error occurs in importRuleList", e)
            return BackupImportResult(ImportResult.VERSION_MISSED)
        } catch (e: VersionInvalidException) {
            XLog.e("Error occurs in importRuleList", e)
            return BackupImportResult(ImportResult.VERSION_UNKNOWN)
        } catch (e: BackupInvalidException) {
            XLog.e("Error occurs in importRuleList", e)
            return BackupImportResult(ImportResult.BACKUP_INVALID)
        } finally {
            if (ruleImporter != null) {
                ruleImporter.close()
            }
        }
    }

    /**
     * 获取导入规则列表的 SAF (Storage Access Framework) 的 Intent
     */
    @JvmStatic
    fun getImportRuleListSAFIntent(context: Context): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(BACKUP_MIME_TYPE, BACKUP_ZIP_MIME_TYPE))
        intent.putExtra(Intent.EXTRA_TITLE, getDefaultBackupFilename(context))

        return intent
    }

    @JvmStatic
    fun restoreDatabaseFromBackup(context: Context, uri: Uri): Boolean {
        context.contentResolver.openInputStream(uri)?.use { raw ->
            val pb = PushbackInputStream(BufferedInputStream(raw), 4)
            val header = ByteArray(4)
            val readCount = pb.read(header)
            if (readCount > 0) {
                pb.unread(header, 0, readCount)
            }
            val isZip = readCount == 4 &&
                header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte()
            if (!isZip) {
                XLog.w("restoreDatabaseFromBackup skipped: not zip uri=%s", uri.toString())
                return false
            }

            val dbDir = context.getDatabasePath(DB_FILE_NAME).parentFile
                ?: throw IllegalStateException("database dir unavailable")
            if (!dbDir.exists() && !dbDir.mkdirs()) {
                throw IllegalStateException("create database dir failed: ${dbDir.absolutePath}")
            }

            val tmpDir = File(context.cacheDir, "db_restore_tmp").apply {
                if (exists()) {
                    deleteRecursively()
                }
                mkdirs()
            }

            try {
                var foundMainDb = false
                ZipInputStream(pb).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val baseName = entry.name.substringAfterLast('/').substringAfterLast('\\')
                            if (
                                baseName == DB_FILE_NAME ||
                                baseName == "$DB_FILE_NAME-wal" ||
                                baseName == "$DB_FILE_NAME-shm"
                            ) {
                                val outFile = File(tmpDir, baseName)
                                outFile.outputStream().use { output -> zis.copyTo(output) }
                                if (baseName == DB_FILE_NAME) {
                                    foundMainDb = true
                                }
                            }
                        }
                        entry = zis.nextEntry
                    }
                }

                if (!foundMainDb) {
                    XLog.w("restoreDatabaseFromBackup failed: main db not found in zip")
                    return false
                }

                AppDatabase.closeInstance()
                DBManager.resetInstance()

                listOf(DB_FILE_NAME, "$DB_FILE_NAME-wal", "$DB_FILE_NAME-shm").forEach { name ->
                    runCatching { File(dbDir, name).delete() }
                }

                var copiedMainDb = false
                tmpDir.listFiles().orEmpty().forEach { src ->
                    val dst = File(dbDir, src.name)
                    src.copyTo(dst, overwrite = true)
                    if (src.name == DB_FILE_NAME) copiedMainDb = true
                }
                XLog.i(
                    "restoreDatabaseFromBackup copied: mainDb=%s path=%s",
                    copiedMainDb,
                    dbDir.absolutePath,
                )
                return copiedMainDb
            } finally {
                runCatching { tmpDir.deleteRecursively() }
            }
        }
        XLog.w("restoreDatabaseFromBackup failed: openInputStream null uri=%s", uri.toString())
        return false
    }

    @JvmStatic
    fun shareBackupFile(context: Context, file: File) {
        val intent = Intent(Intent.ACTION_SEND)

        val authority = context.packageName + ".files"
        val uri = FileProvider.getUriForFile(context, authority, file)
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.type = BACKUP_MIME_TYPE
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(Intent.createChooser(intent, null))
    }

    private fun resolveWarning(backupAppVersion: String, currentAppVersion: String): ImportWarning? {
        val backupMajor = backupAppVersion.split(".").firstOrNull()?.toIntOrNull()
        val currentMajor = currentAppVersion.split(".").firstOrNull()?.toIntOrNull()
        if (backupMajor == null || currentMajor == null) return null
        return if (backupMajor != currentMajor) ImportWarning.APP_VERSION_MISMATCH else null
    }

    @Throws(IOException::class)
    private fun exportBackupZipWithDatabase(
        context: Context,
        uri: Uri,
        ruleList: List<BackupRule>,
        preferences: Map<String, String?>?,
        records: List<BackupSmsRecord>?,
        appVersion: String,
    ) {
        // Best-effort checkpoint before snapshotting db files.
        runCatching {
            AppDatabase.getInstance(context).openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")
        }.onFailure {
            XLog.w("wal checkpoint failed: %s", it.message ?: it.javaClass.simpleName)
        }

        val payloadBytes = JsonUtils.json.encodeToString(
            BackupPayload.serializer(),
            BackupPayload(
                version = BackupConst.BACKUP_VERSION,
                schemaVersion = BackupConst.BACKUP_VERSION,
                appVersion = appVersion,
                rules = ruleList,
                preferences = preferences,
                records = records,
            ),
        ).toByteArray(Charsets.UTF_8)

        val dbFiles = collectDatabaseFiles(context)
        if (dbFiles.none { it.second.name == DB_FILE_NAME }) {
            throw IllegalStateException("database file missing: $DB_FILE_NAME")
        }

        context.contentResolver.openOutputStream(uri)?.use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry(BACKUP_PAYLOAD_ENTRY))
                zip.write(payloadBytes)
                zip.closeEntry()

                dbFiles.forEach { (entryName, file) ->
                    zip.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: throw IOException("openOutputStream returned null: $uri")
    }

    private fun collectDatabaseFiles(context: Context): List<Pair<String, File>> {
        val names = listOf(DB_FILE_NAME, "$DB_FILE_NAME-wal", "$DB_FILE_NAME-shm")
        return names.mapNotNull { name ->
            val file = context.getDatabasePath(name)
            if (file.exists() && file.isFile && file.canRead()) {
                "database/$name" to file
            } else {
                null
            }
        }
    }

    private fun readPayloadBytes(context: Context, uri: Uri): ByteArray? {
        context.contentResolver.openInputStream(uri)?.use { raw ->
            val pb = PushbackInputStream(BufferedInputStream(raw), 4)
            val header = ByteArray(4)
            val readCount = pb.read(header)
            if (readCount > 0) {
                pb.unread(header, 0, readCount)
            }
            val isZip = readCount == 4 &&
                header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte()
            if (!isZip) {
                return pb.readBytes()
            }
            ZipInputStream(pb).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase(Locale.ROOT)
                    if (!entry.isDirectory && (name.endsWith(".scebak") || name.endsWith(".json"))) {
                        return zis.readBytes()
                    }
                    entry = zis.nextEntry
                }
            }
            XLog.e("importRuleList failed: zip payload entry not found")
            return null
        }
        XLog.e("importRuleList failed: openInputStream null, uri=%s", uri.toString())
        return null
    }
}
