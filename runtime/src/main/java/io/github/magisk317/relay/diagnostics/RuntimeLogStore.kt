package io.github.magisk317.relay.diagnostics

import android.content.Context
import android.util.Log
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.smscode.runtime.common.utils.StorageUtils
import io.github.magisk317.relay.runtime.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RuntimeLogEntry(
    val timestamp: Long,
    val priority: Int,
    val tag: String,
    val message: String,
    val route: String = RuntimeLogStore.ROUTE_APP,
)

object RuntimeLogStore {
    private const val INTERNAL_TAG = "RuntimeLogStore"
    private const val LOG_FILE_NAME = "runtime.log"
    private const val PREFS_NAME = "xposed_prefs"
    private const val MAX_BUFFER_SIZE = 1200
    private const val BYTES_PER_MB = 1024 * 1024L
    private const val MAX_SIZE_REFRESH_INTERVAL_MS = 5_000L
    private const val MAX_READ_LINES = 2000
    private const val DEFAULT_TRIM_BUFFER_SIZE = 8 * 1024
    const val ROUTE_SMS_HOOK = "sms_hook"
    const val ROUTE_NMS_HOOK = "nms_hook"
    const val ROUTE_SYSTEM_INPUT = "system_input"
    const val ROUTE_PERMISSION_HOOK = "permission_hook"
    const val ROUTE_FORWARD = "forward"
    const val ROUTE_SENDER = "sender"
    const val ROUTE_ROOT_DB = "root_db"
    const val ROUTE_APP = "app"
    private const val FILE_TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS"
    private val lock = Any()
    private val buffer = ArrayDeque<RuntimeLogEntry>(MAX_BUFFER_SIZE)
    private val pendingFileEntries = ArrayDeque<RuntimeLogEntry>(MAX_BUFFER_SIZE)
    private val fileTimestampFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat(FILE_TIMESTAMP_PATTERN, Locale.getDefault())
    }

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var maxFileSizeBytes: Long = PrefConst.RUNTIME_LOG_FILE_SIZE_MB_DEFAULT * BYTES_PER_MB

    @Volatile
    private var lastSizeRefreshAtMs: Long = 0L

    private fun isModuleContext(context: Context?): Boolean {
        return context?.packageName == BuildConfig.APPLICATION_ID
    }

    fun initialize(context: Context, enableDetailedLogs: Boolean) {
        val resolvedContext = resolveModuleContext(context)
        appContext = resolvedContext.takeIf { isModuleContext(it) }
        enabled = enableDetailedLogs
        setMaxFileSizeMb(readConfiguredMaxFileSizeMb(appContext ?: context))
        flushPendingIfPossible()
    }

    fun setEnabled(on: Boolean) {
        enabled = on
    }

    fun isEnabled(): Boolean = enabled

    fun setMaxFileSizeMb(sizeMb: Int) {
        maxFileSizeBytes = normalizeMaxFileSizeMb(sizeMb).toLong() * BYTES_PER_MB
    }

    fun append(
        priority: Int,
        tag: String,
        message: String,
        force: Boolean = false,
        route: String? = null,
    ) {
        if (!enabled && !force) return
        runCatching {
            val normalizedRoute = normalizeRoute(route)
            val entry = RuntimeLogEntry(
                timestamp = System.currentTimeMillis(),
                priority = priority,
                tag = tag,
                message = message,
                route = normalizedRoute,
            )
            synchronized(lock) {
                buffer.addLast(entry)
                while (buffer.size > MAX_BUFFER_SIZE) {
                    buffer.removeFirst()
                }
                if (!appendToFilesLocked(entry)) {
                    pendingFileEntries.addLast(entry)
                    while (pendingFileEntries.size > MAX_BUFFER_SIZE) {
                        pendingFileEntries.removeFirst()
                    }
                    return@synchronized
                }
                flushPendingLocked()
            }
        }.onFailure {
            Log.w(
                INTERNAL_TAG,
                "append skipped due to storage exception: ${it.message ?: it.javaClass.simpleName}",
            )
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            pendingFileEntries.clear()
        }
        val logDir = getLogDir() ?: return
        runCatching {
            logDir.listFiles()
                ?.filter { it.name.startsWith("runtime.") || it.name == LOG_FILE_NAME }
                ?.forEach { it.writeText("") }
        }
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
        val context = ensureAppContext() ?: return null
        val dir = StorageUtils.getLogDir(context) ?: return null
        return File(dir, LOG_FILE_NAME)
    }

    private fun getLogDir(): File? {
        val context = ensureAppContext() ?: return null
        return StorageUtils.getLogDir(context)
    }

    private fun ensureAppContext(): Context? = synchronized(lock) {
        val existing = appContext
        if (isModuleContext(existing)) {
            return@synchronized existing
        }
        val resolved = resolveContextFromActivityThreadLocked() ?: return@synchronized null
        if (!isModuleContext(resolved)) {
            return@synchronized null
        }
        appContext = resolved
        return@synchronized resolved
    }

    private fun resolveContextFromActivityThreadLocked(): Context? {
        val application = runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentApplication = activityThreadClass.getDeclaredMethod("currentApplication")
            currentApplication.isAccessible = true
            currentApplication.invoke(null) as? Context
        }.getOrNull() ?: return null
        return resolveModuleContext(application)
    }

    private fun resolveModuleContext(context: Context): Context {
        val app = context.applicationContext ?: context
        if (app.packageName == BuildConfig.APPLICATION_ID) return app
        return runCatching {
            app.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrElse { app }
    }

    private fun appendToFilesLocked(entry: RuntimeLogEntry): Boolean {
        return runCatching {
            val context = appContext ?: resolveContextFromActivityThreadLocked() ?: return false
            if (!isModuleContext(context)) return false
            if (appContext == null) {
                appContext = context
            }
            maybeRefreshMaxFileSizeLocked(context)
            val logDir = StorageUtils.getLogDir(context) ?: return false
            val line = encode(entry) + "\n"
            writeLineToFile(File(logDir, LOG_FILE_NAME), line)
            writeLineToFile(File(logDir, routeFileName(entry.route)), line)
            true
        }.onFailure {
            Log.w(
                INTERNAL_TAG,
                "appendToFilesLocked skipped due to storage exception: ${it.message ?: it.javaClass.simpleName}",
            )
        }.getOrDefault(false)
    }

    private fun flushPendingIfPossible() {
        synchronized(lock) {
            flushPendingLocked()
        }
    }

    private fun flushPendingLocked() {
        if (pendingFileEntries.isEmpty()) return
        runCatching {
            val context = appContext ?: resolveContextFromActivityThreadLocked() ?: return
            if (!isModuleContext(context)) return
            if (appContext == null) {
                appContext = context
            }
            maybeRefreshMaxFileSizeLocked(context)
            val logDir = StorageUtils.getLogDir(context) ?: return
            while (pendingFileEntries.isNotEmpty()) {
                val entry = pendingFileEntries.removeFirst()
                val line = encode(entry) + "\n"
                writeLineToFile(File(logDir, LOG_FILE_NAME), line)
                writeLineToFile(File(logDir, routeFileName(entry.route)), line)
            }
        }.onFailure {
            Log.w(
                INTERNAL_TAG,
                "flushPendingLocked skipped due to storage exception: ${it.message ?: it.javaClass.simpleName}",
            )
        }
    }

    private fun writeLineToFile(file: File, line: String) {
        runCatching {
            ensureParentDir(file)
            file.appendText(line)
            trimIfTooLarge(file)
            StorageUtils.setFileWorldReadable(file, 2)
            StorageUtils.setFileWorldWritable(file, 1)
        }
    }

    private fun ensureParentDir(file: File) {
        val parent = file.parentFile ?: return
        if (!parent.exists()) parent.mkdirs()
    }

    private fun maybeRefreshMaxFileSizeLocked(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastSizeRefreshAtMs < MAX_SIZE_REFRESH_INTERVAL_MS) return
        lastSizeRefreshAtMs = now
        setMaxFileSizeMb(readConfiguredMaxFileSizeMb(context))
    }

    private fun readConfiguredMaxFileSizeMb(context: Context): Int {
        val defaultValue = PrefConst.RUNTIME_LOG_FILE_SIZE_MB_DEFAULT
        val prefs = runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull() ?: return defaultValue
        val raw = prefs.all[PrefConst.KEY_RUNTIME_LOG_FILE_SIZE_MB]
        val value = when (raw) {
            is Int -> raw
            is Long -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> defaultValue
        } ?: defaultValue
        return normalizeMaxFileSizeMb(value)
    }

    private fun normalizeMaxFileSizeMb(value: Int): Int {
        return value.coerceAtLeast(PrefConst.RUNTIME_LOG_FILE_SIZE_MB_MIN)
    }

    private fun trimIfTooLarge(file: File) {
        val limitBytes = maxFileSizeBytes
        if (!file.exists() || limitBytes <= 0L) return
        val length = file.length()
        if (length <= limitBytes) return
        val keepBytes = limitBytes.coerceAtLeast(1L)
        val startOffset = length - keepBytes
        val parent = file.parentFile ?: return
        val tmp = File(parent, "${file.name}.tmp")
        runCatching {
            RandomAccessFile(file, "r").use { input ->
                input.seek(startOffset)
                FileOutputStream(tmp, false).use { output ->
                    val buffer = ByteArray(DEFAULT_TRIM_BUFFER_SIZE)
                    var remaining = keepBytes
                    while (remaining > 0L) {
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = input.read(buffer, 0, toRead)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        remaining -= read.toLong()
                    }
                    output.fd.sync()
                }
            }
            if (file.exists() && !file.delete()) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            } else if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            StorageUtils.setFileWorldReadable(file, 2)
            StorageUtils.setFileWorldWritable(file, 1)
        }.onFailure {
            runCatching { if (tmp.exists()) tmp.delete() }
        }
    }

    private fun encode(entry: RuntimeLogEntry): String {
        return buildString {
            append(formatFileTimestamp(entry.timestamp))
            append('\t')
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

        val hasFormattedTimestamp = parts.size >= 5 && parts[1].toLongOrNull() != null
        val tsIndex = if (hasFormattedTimestamp) 1 else 0
        val priorityIndex = tsIndex + 1
        val tagIndex = tsIndex + 2
        val messageIndex = tsIndex + 3

        if (parts.size <= messageIndex) return null

        val ts = parts[tsIndex].toLongOrNull() ?: return null
        val priority = parts[priorityIndex].toIntOrNull() ?: Log.INFO
        val tag = unescape(parts[tagIndex])
        val message = unescape(parts.subList(messageIndex, parts.size).joinToString("\t"))
        return RuntimeLogEntry(ts, priority, tag, message, ROUTE_APP)
    }

    private fun formatFileTimestamp(timestamp: Long): String {
        return fileTimestampFormatter.get()?.format(Date(timestamp)) ?: timestamp.toString()
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

    private fun normalizeRoute(route: String?): String {
        val normalized = route?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return when (normalized) {
            ROUTE_SMS_HOOK,
            ROUTE_NMS_HOOK,
            ROUTE_SYSTEM_INPUT,
            ROUTE_PERMISSION_HOOK,
            ROUTE_FORWARD,
            ROUTE_SENDER,
            ROUTE_ROOT_DB,
            ROUTE_APP,
            -> normalized

            else -> ROUTE_APP
        }
    }

    private fun routeFileName(route: String): String {
        return when (normalizeRoute(route)) {
            ROUTE_SMS_HOOK -> "runtime.sms_hook.log"
            ROUTE_NMS_HOOK -> "runtime.nms_hook.log"
            ROUTE_SYSTEM_INPUT -> "runtime.system_input.log"
            ROUTE_PERMISSION_HOOK -> "runtime.permission_hook.log"
            ROUTE_FORWARD -> "runtime.forward.log"
            ROUTE_SENDER -> "runtime.sender.log"
            ROUTE_ROOT_DB -> "runtime.root_db.log"
            else -> "runtime.app.log"
        }
    }

    fun routeFromCallerClassName(className: String?): String {
        val value = className.orEmpty()
        return when {
            value.contains(".xp.hook.forward.") -> ROUTE_FORWARD
            value.contains(".xp.hook.code.") -> ROUTE_SMS_HOOK
            value.contains(".xp.hook.telephony.") -> ROUTE_SMS_HOOK
            value.contains(".xp.LibXposedEntry") -> ROUTE_SMS_HOOK
            value.contains(".xp.hook.notification.") ||
                value.contains(".core.hook.notification.") ||
                value.contains(".xposed.hook.notification.") -> ROUTE_NMS_HOOK
            value.contains(".xp.hook.system.") ||
                value.contains(".core.hook.system.") ||
                value.contains(".xposed.hook.system.") -> ROUTE_SYSTEM_INPUT
            value.contains(".xp.hook.permission.") ||
                value.contains(".core.hook.permission.") ||
                value.contains(".xposed.hook.permission.") -> ROUTE_PERMISSION_HOOK
            value.contains(".domain.recovery.") || value.contains(".forwarder.recovery.") -> ROUTE_ROOT_DB
            else -> ROUTE_APP
        }
    }

}
