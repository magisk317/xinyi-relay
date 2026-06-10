package io.github.magisk317.relay.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.magisk317.relay.auth.AuthManager
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.backup.drive.GoogleDriveBackupConfig
import io.github.magisk317.relay.contract.json.RelayJson
import io.github.magisk317.relay.data.backup.BackupManager
import io.github.magisk317.smscode.runtime.contract.backup.ExportResult
import io.github.magisk317.smscode.runtime.contract.backup.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DriveBackupMeta(
    val id: String,
    val name: String,
    val size: Long,
    val modifiedTime: String,
)

class GoogleDriveBackupManager(
    private val context: Context,
    private val authManager: AuthManager,
) {
    private val client = OkHttpClient()
    private var config: GoogleDriveBackupConfig = GoogleDriveBackupConfig()

    fun updateConfig(newConfig: GoogleDriveBackupConfig) {
        config = newConfig
    }

    private suspend fun getAccessToken(): String? {
        return authManager.getGoogleDriveAccessToken()
    }

    private suspend fun authHeader(): String {
        val token = getAccessToken() ?: throw IllegalStateException("Not logged in or missing access token")
        return "Bearer $token"
    }

    private fun tempFileUri(name: String): Pair<File, Uri> {
        val file = File(context.cacheDir, name)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return file to uri
    }

    suspend fun uploadBackup(
        includeConfig: Boolean = true,
        includeRules: Boolean = true,
        includeRecords: Boolean = true,
        includeDatabase: Boolean = true,
    ): Result<DriveBackupMeta> = withContext(Dispatchers.IO) {
        runCatching {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "Relay-Cloud-$timestamp.zip"

            val (tempFile, tempUri) = tempFileUri(fileName)
            try {
                // Export to temp file via BackupManager
                val appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
                val exportResult = BackupManager.exportBackup(
                    context = context,
                    uri = tempUri,
                    ruleList = emptyList(), // BackupManager reads from DB internally
                    preferences = null,
                    records = null,
                    appVersion = appVersion,
                    includeDatabase = includeDatabase,
                )
                if (exportResult != ExportResult.SUCCESS) {
                    throw IllegalStateException("Local backup export failed: $exportResult")
                }
                val fileSize = tempFile.length()
                if (!tempFile.isFile || fileSize <= 0L) {
                    throw IllegalStateException("Local backup file missing or empty")
                }
                XLog.i("Google Drive backup export ready: name=%s size=%d", fileName, fileSize)

                val folderId = ensureFolderPath(config.folderPath)

                // Upload to a user-visible Drive folder.
                val metadata = buildJsonObject {
                    put("name", fileName)
                    put("parents", JsonArray(listOf(JsonPrimitive(folderId))))
                }.toString()
                val requestBody = MultipartBody.Builder()
                    .setType("multipart/related".toMediaType())
                    .addPart(metadata.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .addPart(tempFile.asRequestBody("application/zip".toMediaType()))
                    .build()

                val request = Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                    .header("Authorization", authHeader())
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        XLog.e(
                            "Google Drive backup upload failed: code=%d body=%s",
                            response.code,
                            body.take(MAX_LOG_BODY_LENGTH),
                        )
                        if (body.isDriveApiDisabledError()) {
                            throw IllegalStateException("Google Drive API disabled")
                        }
                        throw IllegalStateException("Upload failed: ${response.code}")
                    }
                    val fileId = parseObject(body).string("id")
                    require(fileId.isNotBlank()) { "Upload response missing file id" }
                    XLog.i("Google Drive backup upload success: name=%s size=%d", fileName, fileSize)
                    val modifiedTime = timestamp

                    DriveBackupMeta(
                        id = fileId,
                        name = fileName,
                        size = fileSize,
                        modifiedTime = modifiedTime,
                    )
                }
            } finally {
                if (tempFile.exists()) {
                    val deleted = tempFile.delete()
                    XLog.i("Google Drive backup temp cleanup: name=%s deleted=%s", fileName, deleted)
                }
            }
        }
    }

    suspend fun listBackups(): Result<List<DriveBackupMeta>> = withContext(Dispatchers.IO) {
        runCatching {
            val folderId = findFolderPath(config.folderPath) ?: return@runCatching emptyList()
            val query = "'$folderId' in parents and trashed = false and name contains '.zip'"
            val url = "https://www.googleapis.com/drive/v3/files" +
                "?spaces=drive&pageSize=100" +
                "&q=${query.urlEncoded()}" +
                "&fields=files(id,name,size,modifiedTime)"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", authHeader())
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    XLog.e(
                        "Google Drive backup list failed: code=%d body=%s",
                        response.code,
                        body.take(MAX_LOG_BODY_LENGTH),
                    )
                    if (body.isDriveApiDisabledError()) {
                        throw IllegalStateException("Google Drive API disabled")
                    }
                    throw IllegalStateException("List failed: ${response.code}")
                }

                val files = parseObject(body)["files"] as? JsonArray ?: JsonArray(emptyList())

                files.mapNotNull { file ->
                    val obj = file as? JsonObject ?: return@mapNotNull null
                    val name = obj.string("name")
                    if (!name.endsWith(".zip", ignoreCase = true)) return@mapNotNull null
                    DriveBackupMeta(
                        id = obj.string("id"),
                        name = name,
                        size = obj.long("size"),
                        modifiedTime = obj.string("modifiedTime"),
                    )
                }
            }
        }
    }

    suspend fun downloadBackup(fileId: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                .header("Authorization", authHeader())
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("Download failed: ${response.code}")
            }

            val file = File(context.cacheDir, "backup-$fileId.zip")
            FileOutputStream(file).use { output ->
                response.body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }

            file
        }
    }

    suspend fun deleteBackup(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId")
                .header("Authorization", authHeader())
                .delete()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("Delete failed: ${response.code}")
            }
        }
    }

    suspend fun restoreFromBackup(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val tempFile = downloadBackup(fileId).getOrThrow()
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

    private fun parseObject(raw: String): JsonObject {
        return RelayJson.parseElement(raw) as? JsonObject ?: error("Expected JSON object")
    }

    private suspend fun ensureFolderPath(folderPath: String): String {
        var parentId = ROOT_FOLDER_ID
        normalizeFolderSegments(folderPath).forEach { segment ->
            parentId = findFolder(segment, parentId) ?: createFolder(segment, parentId)
        }
        return parentId
    }

    private suspend fun findFolderPath(folderPath: String): String? {
        var parentId = ROOT_FOLDER_ID
        normalizeFolderSegments(folderPath).forEach { segment ->
            parentId = findFolder(segment, parentId) ?: return null
        }
        return parentId
    }

    private fun normalizeFolderSegments(folderPath: String): List<String> {
        return folderPath
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private suspend fun findFolder(name: String, parentId: String): String? {
        val query = "mimeType = '$DRIVE_FOLDER_MIME_TYPE' and name = '${name.driveQueryEscaped()}' and '$parentId' in parents and trashed = false"
        val url = "https://www.googleapis.com/drive/v3/files" +
            "?spaces=drive&pageSize=1" +
            "&q=${query.urlEncoded()}" +
            "&fields=files(id,name)"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader())
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                logDriveFailure("folder lookup", response.code, body)
                throw IllegalStateException("List failed: ${response.code}")
            }
            val files = parseObject(body)["files"] as? JsonArray ?: JsonArray(emptyList())
            return files.firstOrNull()
                ?.let { it as? JsonObject }
                ?.string("id")
                ?.takeIf { it.isNotBlank() }
        }
    }

    private suspend fun createFolder(name: String, parentId: String): String {
        val metadata = buildJsonObject {
            put("name", name)
            put("mimeType", DRIVE_FOLDER_MIME_TYPE)
            put("parents", JsonArray(listOf(JsonPrimitive(parentId))))
        }.toString()
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?fields=id,name")
            .header("Authorization", authHeader())
            .post(metadata.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                logDriveFailure("folder create", response.code, body)
                throw IllegalStateException("Upload failed: ${response.code}")
            }
            return parseObject(body).string("id").takeIf { it.isNotBlank() }
                ?: error("Create folder response missing id")
        }
    }

    private fun logDriveFailure(operation: String, code: Int, body: String) {
        XLog.e(
            "Google Drive backup %s failed: code=%d body=%s",
            operation,
            code,
            body.take(MAX_LOG_BODY_LENGTH),
        )
        if (body.isDriveApiDisabledError()) {
            throw IllegalStateException("Google Drive API disabled")
        }
    }

    private fun JsonObject.string(name: String): String {
        return this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private fun JsonObject.long(name: String): Long {
        val primitive = this[name]?.jsonPrimitive ?: return 0L
        return primitive.longOrNull ?: primitive.contentOrNull?.toLongOrNull() ?: 0L
    }

    private fun String.isDriveApiDisabledError(): Boolean {
        return contains("Google Drive API has not been used", ignoreCase = true) ||
            contains("it is disabled", ignoreCase = true)
    }

    private fun String.driveQueryEscaped(): String {
        return replace("\\", "\\\\").replace("'", "\\'")
    }

    private fun String.urlEncoded(): String {
        return URLEncoder.encode(this, "UTF-8").replace("+", "%20")
    }

    private companion object {
        const val MAX_LOG_BODY_LENGTH = 512
        const val DRIVE_FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        const val ROOT_FOLDER_ID = "root"
    }
}
