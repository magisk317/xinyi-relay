package com.github.magisk317.smscode.data.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

object UpgradeDownloader {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    data class Progress(
        val bytesRead: Long,
        val totalBytes: Long,
        val percent: Float,
    )

    suspend fun download(
        context: Context,
        versionCode: Long,
        asset: UpgradeApkAsset,
        onProgress: (Progress) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val abiLabel = asset.abi.ifBlank { "universal" }.replace("[^A-Za-z0-9._-]".toRegex(), "_")
        val target = File(updatesDir, "XinyiRelay-v${versionCode}-${abiLabel}.apk")
        val temp = File(target.parentFile, "${target.name}.part")
        if (temp.exists()) temp.delete()

        try {
            val request = Request.Builder()
                .url(asset.downloadUrl)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }
                val body = response.body
                val expectedSize = if (asset.fileSize > 0L) asset.fileSize else body.contentLength()
                FileOutputStream(temp).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var totalRead = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            totalRead += count
                            val percent = if (expectedSize > 0L) {
                                (totalRead.toFloat() / expectedSize.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            onProgress(Progress(totalRead, expectedSize, percent))
                        }
                        output.flush()
                        if (asset.fileSize > 0L && totalRead != asset.fileSize) {
                            throw IOException("size_mismatch expected=${asset.fileSize} actual=$totalRead")
                        }
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                throw IOException("rename_failed")
            }
            cleanupOldPackages(updatesDir, keep = target.name)
            target
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun cleanupOldPackages(dir: File, keep: String) {
        dir.listFiles()
            ?.filter { it.name.endsWith(".apk") && it.name != keep }
            ?.forEach { runCatching { it.delete() } }
    }
}
