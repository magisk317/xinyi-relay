package io.github.magisk317.relay.common.utils

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.util.LinkedHashMap

object SharedRuntimeGate {

    data class ClaimResult(
        val claimed: Boolean,
        val ageMs: Long? = null,
    )

    fun claimWithinWindow(
        context: Context,
        fileName: String,
        key: String,
        windowMs: Long,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
    ): ClaimResult {
        if (key.isBlank()) {
            return ClaimResult(claimed = true)
        }
        val now = System.currentTimeMillis()
        val result = withFileLock(context, fileName) { raf ->
            val entries = readEntries(raf)
            trimExpired(entries, now, windowMs)
            val last = entries[key]
            if (last != null && now - last <= windowMs) {
                writeEntries(raf, entries)
                return@withFileLock ClaimResult(claimed = false, ageMs = now - last)
            }
            entries[key] = now
            trimSize(entries, maxEntries)
            writeEntries(raf, entries)
            ClaimResult(claimed = true)
        }
        return result ?: ClaimResult(claimed = true)
    }

    fun <T> withFileLock(
        context: Context,
        fileName: String,
        block: (RandomAccessFile) -> T,
    ): T? {
        return runCatching {
            val file = File(StorageUtils.getExternalFilesDir(context), fileName)
            file.parentFile?.mkdirs()
            RandomAccessFile(file, "rw").use { raf ->
                raf.channel.use { channel ->
                    channel.lock().use {
                        block(raf)
                    }
                }
            }
        }.onFailure {
            XLog.w(
                "SharedRuntimeGate failed: file=%s err=%s",
                fileName,
                it.message ?: it.javaClass.simpleName,
            )
        }.getOrNull()
    }

    private fun readEntries(raf: RandomAccessFile): LinkedHashMap<String, Long> {
        val entries = LinkedHashMap<String, Long>()
        raf.seek(0L)
        while (true) {
            val rawLine = raf.readLine() ?: break
            val line = rawLine.trim()
            if (line.isBlank()) continue
            val split = line.indexOf('=')
            if (split <= 0) continue
            val key = line.substring(0, split)
            val value = line.substring(split + 1).toLongOrNull() ?: continue
            entries[key] = value
        }
        return entries
    }

    private fun writeEntries(
        raf: RandomAccessFile,
        entries: LinkedHashMap<String, Long>,
    ) {
        raf.setLength(0L)
        raf.seek(0L)
        val content = buildString {
            entries.forEach { (key, value) ->
                append(key)
                append('=')
                append(value)
                append('\n')
            }
        }
        raf.write(content.toByteArray())
    }

    private fun trimExpired(
        entries: LinkedHashMap<String, Long>,
        now: Long,
        windowMs: Long,
    ) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > windowMs) {
                iterator.remove()
            }
        }
    }

    private fun trimSize(
        entries: LinkedHashMap<String, Long>,
        maxEntries: Int,
    ) {
        while (entries.size > maxEntries) {
            val firstKey = entries.entries.firstOrNull()?.key ?: break
            entries.remove(firstKey)
        }
    }

    private const val DEFAULT_MAX_ENTRIES = 256
}
