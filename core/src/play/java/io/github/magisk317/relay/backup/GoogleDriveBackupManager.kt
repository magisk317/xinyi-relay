package io.github.magisk317.relay.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.magisk317.relay.contract.json.RelayJson
import io.github.magisk317.relay.auth.FirebaseAuthManager
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
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
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
    private val authManager: FirebaseAuthManager,
) {
    private val client = OkHttpClient()

    private fun getAccessToken(): String? = authManager.session.value?.idToken

    private fun authHeader(): String {
        val token = getAccessToken() ?: throw IllegalStateException("Not logged in")
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

                // Upload to Drive appDataFolder
                val metadata = buildJsonObject {
                    put("name", fileName)
                    put("parents", JsonArray(listOf(JsonPrimitive("appDataFolder"))))
                }.toString()
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "metadata",
                        "metadata",
                        metadata.toRequestBody("application/json; charset=utf-8".toMediaType()),
                    )
                    .addFormDataPart(
                        "file",
                        fileName,
                        tempFile.asRequestBody("application/zip".toMediaType()),
                    )
                    .build()

                val request = Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                    .header("Authorization", authHeader())
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Upload failed: ${response.code}")
                }

                val body = response.body.string()
                val fileId = parseObject(body).string("id")
                require(fileId.isNotBlank()) { "Upload response missing file id" }
                val fileSize = tempFile.length()
                val modifiedTime = timestamp

                DriveBackupMeta(
                    id = fileId,
                    name = fileName,
                    size = fileSize,
                    modifiedTime = modifiedTime,
                )
            } finally {
                tempFile.delete()
            }
        }
    }

    suspend fun listBackups(): Result<List<DriveBackupMeta>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&pageSize=100&fields=files(id,name,size,modifiedTime)")
                .header("Authorization", authHeader())
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("List failed: ${response.code}")
            }

            val body = response.body.string()
            val files = parseObject(body)["files"] as? JsonArray ?: JsonArray(emptyList())

            files.mapNotNull { file ->
                val obj = file as? JsonObject ?: return@mapNotNull null
                DriveBackupMeta(
                    id = obj.string("id"),
                    name = obj.string("name"),
                    size = obj.long("size"),
                    modifiedTime = obj.string("modifiedTime"),
                )
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

    private fun JsonObject.string(name: String): String {
        return this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private fun JsonObject.long(name: String): Long {
        val primitive = this[name]?.jsonPrimitive ?: return 0L
        return primitive.longOrNull ?: primitive.contentOrNull?.toLongOrNull() ?: 0L
    }
}
