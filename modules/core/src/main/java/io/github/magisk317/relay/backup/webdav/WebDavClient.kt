package io.github.magisk317.relay.backup.webdav

import io.github.magisk317.relay.android.common.utils.XLog
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

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
        val segments = config.remotePath
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        var currentPath = ""
        for (segment in segments) {
            currentPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment"
            val url = config.getDirectoryUrl(currentPath)
            val request = Request.Builder()
                .url(url)
                .method("MKCOL", null)
                .header("Authorization", credentials)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != HTTP_METHOD_NOT_ALLOWED) {
                    val body = response.body.string()
                    XLog.e(
                        "WebDAV MKCOL failed: code=%d path=%s body=%s",
                        response.code,
                        currentPath,
                        body.take(MAX_LOG_BODY_LENGTH),
                    )
                    throw IllegalStateException("Failed to create directory: ${response.code}")
                }
            }
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

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body.string()
                XLog.e(
                    "WebDAV upload failed: code=%d name=%s body=%s",
                    response.code,
                    fileName,
                    body.take(MAX_LOG_BODY_LENGTH),
                )
                throw IllegalStateException("Upload failed: ${response.code}")
            }
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
        return parseMultiStatusResponseWithDom(xml).ifEmpty {
            parseMultiStatusResponseWithRegex(xml)
        }
    }

    private fun parseMultiStatusResponseWithDom(xml: String): List<WebDavFile> {
        val files = mutableListOf<WebDavFile>()
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
            val responses = document.getElementsByTagNameNS("*", "response")
            for (index in 0 until responses.length) {
                val response = responses.item(index)
                val href = response.childText("href") ?: continue
                val displayName = response.childText("displayname")
                val contentLength = response.childText("getcontentlength")?.toLongOrNull() ?: 0L
                val lastModified = response.childText("getlastmodified").orEmpty()
                val isDirectory = response.hasChild("collection")
                val name = (displayName ?: href.substringAfterLast('/').trimEnd('/')).trim()
                if (shouldKeepEntry(name, href, isDirectory)) {
                    files.add(WebDavFile(name, contentLength, lastModified, isDirectory))
                }
            }
            files
        }.getOrElse {
            XLog.w("WebDAV PROPFIND DOM parse failed: %s", it.message ?: it.javaClass.simpleName)
            emptyList()
        }
    }

    private fun parseMultiStatusResponseWithRegex(xml: String): List<WebDavFile> {
        val files = mutableListOf<WebDavFile>()
        val responsePattern = Regex("<(?:\\w+:)?response\\b[^>]*>(.*?)</(?:\\w+:)?response>", RegexOption.DOT_MATCHES_ALL)
        val hrefPattern = Regex("<(?:\\w+:)?href\\b[^>]*>(.*?)</(?:\\w+:)?href>", RegexOption.DOT_MATCHES_ALL)
        val displayNamePattern = Regex("<(?:\\w+:)?displayname\\b[^>]*>(.*?)</(?:\\w+:)?displayname>", RegexOption.DOT_MATCHES_ALL)
        val contentLengthPattern = Regex("<(?:\\w+:)?getcontentlength\\b[^>]*>(.*?)</(?:\\w+:)?getcontentlength>", RegexOption.DOT_MATCHES_ALL)
        val lastModifiedPattern = Regex("<(?:\\w+:)?getlastmodified\\b[^>]*>(.*?)</(?:\\w+:)?getlastmodified>", RegexOption.DOT_MATCHES_ALL)
        val resourceTypePattern = Regex("<(?:\\w+:)?resourcetype\\b[^>]*>(.*?)</(?:\\w+:)?resourcetype>", RegexOption.DOT_MATCHES_ALL)

        responsePattern.findAll(xml).forEach { match ->
            val response = match.value
            val href = hrefPattern.find(response)?.groupValues?.get(1) ?: return@forEach
            val displayName = displayNamePattern.find(response)?.groupValues?.get(1)
            val contentLength = contentLengthPattern.find(response)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val lastModified = lastModifiedPattern.find(response)?.groupValues?.get(1) ?: ""
            val resourceType = resourceTypePattern.find(response)?.groupValues?.get(1) ?: ""
            val isDirectory = resourceType.contains("collection")

            val name = (displayName ?: href.substringAfterLast('/').trimEnd('/')).trim()
            if (shouldKeepEntry(name, href, isDirectory)) {
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

    private fun shouldKeepEntry(name: String, href: String, isDirectory: Boolean): Boolean {
        if (name.isBlank()) return false
        val normalizedHref = href.trimEnd('/')
        val normalizedPath = config.remotePath.trim('/').takeIf { it.isNotBlank() } ?: return true
        if (isDirectory && normalizedHref.endsWith(normalizedPath)) return false
        return true
    }

    private fun org.w3c.dom.Node.childText(localName: String): String? {
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child.localName == localName) {
                return child.textContent?.trim()
            }
            val nested = child.childText(localName)
            if (nested != null) return nested
        }
        return null
    }

    private fun org.w3c.dom.Node.hasChild(localName: String): Boolean {
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child.localName == localName) return true
            if (child.hasChild(localName)) return true
        }
        return false
    }

    private companion object {
        const val HTTP_METHOD_NOT_ALLOWED = 405
        const val MAX_LOG_BODY_LENGTH = 512
    }
}
