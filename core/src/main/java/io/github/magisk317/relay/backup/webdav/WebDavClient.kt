package io.github.magisk317.relay.backup.webdav

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
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

class WebDavClient(private val config: WebDavConfig) {

    private val client = OkHttpClient()
    private val credentials = Credentials.basic(config.username, config.password)

    data class WebDavFile(
        val name: String,
        val size: Long,
        val lastModified: String,
        val isDirectory: Boolean,
    )

    suspend fun ensureDirectory(): Result<Unit> = runCatching {
        val url = config.getDirectoryUrl()
        val request = Request.Builder()
            .url(url)
            .method("MKCOL", null)
            .header("Authorization", credentials)
            .build()

        val response = client.newCall(request).execute()
        // 405 means directory already exists
        if (!response.isSuccessful && response.code != 405) {
            throw IllegalStateException("Failed to create directory: ${response.code}")
        }
    }

    suspend fun uploadFile(file: File, fileName: String): Result<Unit> = runCatching {
        ensureDirectory().getOrThrow()

        val url = config.getFullUrl(fileName)
        val requestBody = file.asRequestBody("application/zip".toMediaType())

        val request = Request.Builder()
            .url(url)
            .header("Authorization", credentials)
            .put(requestBody)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("Upload failed: ${response.code}")
        }
    }

    suspend fun downloadFile(fileName: String): Result<File> = runCatching {
        val url = config.getFullUrl(fileName)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", credentials)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("Download failed: ${response.code}")
        }

        val tempFile = File.createTempFile("webdav-backup", ".zip")
        FileOutputStream(tempFile).use { output ->
            response.body.byteStream().use { input ->
                input.copyTo(output)
            }
        }
        tempFile
    }

    suspend fun deleteFile(fileName: String): Result<Unit> = runCatching {
        val url = config.getFullUrl(fileName)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", credentials)
            .delete()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("Delete failed: ${response.code}")
        }
    }

    suspend fun listFiles(): Result<List<WebDavFile>> = runCatching {
        val url = config.getDirectoryUrl()
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:">
                <D:prop>
                    <D:displayname/>
                    <D:getcontentlength/>
                    <D:getlastmodified/>
                    <D:resourcetype/>
                </D:prop>
            </D:propfind>
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", body.toRequestBody("application/xml; charset=utf-8".toMediaType()))
            .header("Authorization", credentials)
            .header("Depth", "1")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("List failed: ${response.code}")
        }

        parseMultiStatusResponse(response.body.string())
    }

    private fun parseMultiStatusResponse(xml: String): List<WebDavFile> {
        val files = mutableListOf<WebDavFile>()

        // Simple XML parsing for DAV response
        val responsePattern = Regex("<D:response>(.*?)</D:response>", RegexOption.DOT_MATCHES_ALL)
        val hrefPattern = Regex("<D:href>(.*?)</D:href>")
        val displayNamePattern = Regex("<D:displayname>(.*?)</D:displayname>")
        val contentLengthPattern = Regex("<D:getcontentlength>(.*?)</D:getcontentlength>")
        val lastModifiedPattern = Regex("<D:getlastmodified>(.*?)</D:getlastmodified>")
        val resourceTypePattern = Regex("<D:resourcetype>(.*?)</D:resourcetype>", RegexOption.DOT_MATCHES_ALL)

        responsePattern.findAll(xml).forEach { match ->
            val response = match.value
            val href = hrefPattern.find(response)?.groupValues?.get(1) ?: return@forEach
            val displayName = displayNamePattern.find(response)?.groupValues?.get(1)
            val contentLength = contentLengthPattern.find(response)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val lastModified = lastModifiedPattern.find(response)?.groupValues?.get(1) ?: ""
            val resourceType = resourceTypePattern.find(response)?.groupValues?.get(1) ?: ""
            val isDirectory = resourceType.contains("collection")

            // Skip the directory itself
            if (isDirectory && href.endsWith(config.remotePath.trimStart('/'))) {
                return@forEach
            }

            val name = displayName ?: href.substringAfterLast('/').trimEnd('/')
            if (name.isNotBlank()) {
                files.add(
                    WebDavFile(
                        name = name,
                        size = contentLength,
                        lastModified = lastModified,
                        isDirectory = isDirectory,
                    ),
                )
            }
        }

        return files
    }
}
