package com.github.magisk317.smscode.common.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RuntimeLogEntry(
    val timestamp: Long,
    val priority: Int,
    val tag: String,
    val message: String,
)

object RuntimeLogStore {
    private const val LOG_FILE_NAME = "runtime.log"
    private const val MAX_BUFFER_SIZE = 1200
    private const val MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024L
    private const val MAX_READ_LINES = 2000
    private val lock = Any()
    private val buffer = ArrayDeque<RuntimeLogEntry>(MAX_BUFFER_SIZE)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var enabled: Boolean = false

    fun initialize(context: Context, enableDetailedLogs: Boolean) {
        appContext = context.applicationContext ?: context
        enabled = enableDetailedLogs
    }

    fun setEnabled(on: Boolean) {
        enabled = on
    }

    fun isEnabled(): Boolean = enabled

    fun append(priority: Int, tag: String, message: String, force: Boolean = false) {
        if (!enabled && !force) return
        val entry = RuntimeLogEntry(
            timestamp = System.currentTimeMillis(),
            priority = priority,
            tag = tag,
            message = message,
        )
        synchronized(lock) {
            buffer.addLast(entry)
            while (buffer.size > MAX_BUFFER_SIZE) {
                buffer.removeFirst()
            }
        }
        appendToFile(entry)
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
        }
        val file = getLogFile() ?: return
        runCatching { file.writeText("") }
    }

    fun query(minutes: Int?, keyword: String?, limit: Int = 600): List<RuntimeLogEntry> {
        val memoryList = synchronized(lock) { buffer.toList() }
        val source = if (memoryList.isNotEmpty()) memoryList else readFromFile()
        val now = System.currentTimeMillis()
        val cutoff = if (minutes == null || minutes <= 0) Long.MIN_VALUE else now - minutes * 60_000L
        val normalizedKeyword = keyword?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return source.asSequence()
            .filter { it.timestamp >= cutoff }
            .filter {
                if (normalizedKeyword.isEmpty()) {
                    true
                } else {
                    it.tag.lowercase(Locale.ROOT).contains(normalizedKeyword) ||
                        it.message.lowercase(Locale.ROOT).contains(normalizedKeyword)
                }
            }
            .toList()
            .takeLast(limit)
    }

    fun exportText(minutes: Int?, keyword: String?, limit: Int = 600): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        val lines = query(minutes = minutes, keyword = keyword, limit = limit).map { entry ->
            val ts = formatter.format(Date(entry.timestamp))
            "$ts [${priorityName(entry.priority)}/${entry.tag}] ${entry.message}"
        }
        return lines.joinToString(separator = "\n\n")
    }

    fun exportToFile(context: Context, minutes: Int?, keyword: String?, limit: Int = 1200): File? {
        val dir = StorageUtils.getPrivateLogExportDir(context)
        if (!dir.exists() && !dir.mkdirs()) return null

        val fileName = "runtime_log_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt"
        val file = File(dir, fileName)
        val content = exportText(minutes = minutes, keyword = keyword, limit = limit)
        return runCatching {
            file.writeText(content.ifBlank { "No logs." })
            StorageUtils.setFileWorldReadable(file, 2)
            file
        }.getOrNull()
    }

    private fun appendToFile(entry: RuntimeLogEntry) {
        val file = getLogFile() ?: return
        runCatching {
            ensureParentDir(file)
            rotateIfTooLarge(file)
            file.appendText(encode(entry) + "\n")
            StorageUtils.setFileWorldReadable(file, 2)
        }
    }

    private fun readFromFile(): List<RuntimeLogEntry> {
        val file = getLogFile() ?: return emptyList()
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines()
                .takeLast(MAX_READ_LINES)
                .mapNotNull { decode(it) }
        }.getOrDefault(emptyList())
    }

    private fun getLogFile(): File? {
        val context = appContext ?: return null
        val dir = StorageUtils.getLogDir(context) ?: return null
        return File(dir, LOG_FILE_NAME)
    }

    private fun ensureParentDir(file: File) {
        val parent = file.parentFile ?: return
        if (!parent.exists()) parent.mkdirs()
    }

    private fun rotateIfTooLarge(file: File) {
        if (!file.exists() || file.length() < MAX_FILE_SIZE_BYTES) return
        val backup = File(file.parentFile, "$LOG_FILE_NAME.bak")
        runCatching {
            if (backup.exists()) backup.delete()
            file.renameTo(backup)
            file.writeText("")
        }
    }

    private fun encode(entry: RuntimeLogEntry): String {
        return buildString {
            append(entry.timestamp)
            append('\t')
            append(entry.priority)
            append('\t')
            append(escape(entry.tag))
            append('\t')
            append(escape(entry.message))
        }
    }

    private fun decode(line: String): RuntimeLogEntry? {
        val parts = line.split('\t')
        if (parts.size < 4) return null
        val ts = parts[0].toLongOrNull() ?: return null
        val priority = parts[1].toIntOrNull() ?: Log.INFO
        val tag = unescape(parts[2])
        val message = unescape(parts.subList(3, parts.size).joinToString("\t"))
        return RuntimeLogEntry(ts, priority, tag, message)
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\t", "\\t")
            .replace("\n", "\\n")
    }

    private fun unescape(value: String): String {
        return value
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }

    private fun priorityName(priority: Int): String {
        return when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> priority.toString()
        }
    }
}
