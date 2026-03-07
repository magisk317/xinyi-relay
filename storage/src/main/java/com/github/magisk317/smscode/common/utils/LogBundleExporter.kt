package com.github.magisk317.smscode.common.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object LogBundleExporter {
    private const val ZIP_MIME_TYPE = "application/zip"
    private val LSPOSED_LOG_DIRS = listOf(
        "/data/adb/lspd/log",
        "/data/adb/lspd/log.old",
    )
    private val opLock = Any()

    data class ExportResult(
        val file: File?,
        val details: String,
    )

    data class ClearResult(
        val success: Boolean,
        val details: String,
    )

    /**
     * Build a zip bundle containing app logs + LSPosed logs.
     * Returns [ExportResult.file] as null when build fails.
     */
    @Suppress("TooGenericExceptionCaught")
    fun buildLogBundle(context: Context): ExportResult {
        synchronized(opLock) {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val exportDir = getPrivateExportDir(context)
            if (!ensureDirectory(exportDir, recreateWhenFile = true)) {
                val details = "export root unavailable: ${exportDir.absolutePath}"
                XLog.e("buildLogBundle failed: %s", details)
                return ExportResult(null, details)
            }
            val stagingDir = File(exportDir, ".tmp_logs_$timestamp").apply {
                if (exists()) deleteRecursively()
            }
            if (!ensureDirectory(stagingDir, recreateWhenFile = true)) {
                val details = "staging dir unavailable: ${stagingDir.absolutePath}"
                XLog.e("buildLogBundle failed: %s", details)
                return ExportResult(null, details)
            }
            val details = mutableListOf<String>()

            try {
                val appLogSrc = StorageUtils.getLogDir(context)
                if (appLogSrc != null && appLogSrc.exists()) {
                    copyDirectory(appLogSrc, File(stagingDir, "app/log"))
                    details += "app log: ${appLogSrc.absolutePath}"
                } else {
                    details += "app log missing"
                }

                val crashLogSrc = StorageUtils.getCrashLogDir(context)
                if (crashLogSrc != null && crashLogSrc.exists()) {
                    copyDirectory(crashLogSrc, File(stagingDir, "app/crash"))
                    details += "crash log: ${crashLogSrc.absolutePath}"
                } else {
                    details += "crash log missing"
                }

                val lsposedCopied = copyLsposedLogs(stagingDir, details)
                if (!lsposedCopied) {
                    details += "lsposed log missing or unreadable"
                }

                File(stagingDir, "summary.txt").writeText(
                    buildString {
                        appendLine("Export Time: $timestamp")
                        appendLine("Package: ${context.packageName}")
                        details.forEach { appendLine("- $it") }
                    },
                )

                val zipFile = File(exportDir, "logs_$timestamp.zip")
                zipDirectory(stagingDir, zipFile)
                StorageUtils.setFileWorldReadable(zipFile, 1)
                XLog.i(
                    "buildLogBundle success: file=%s size=%d details=%s",
                    zipFile.absolutePath,
                    zipFile.length(),
                    details.joinToString("; "),
                )
                return ExportResult(zipFile, details.joinToString("; "))
            } catch (t: Throwable) {
                XLog.e("buildLogBundle failed", t)
                return ExportResult(null, t.message ?: t.javaClass.simpleName)
            } finally {
                runCatching {
                    if (!deleteRecursivelyWithSuFallback(stagingDir)) {
                        XLog.w("Failed to cleanup staging dir: %s", stagingDir.absolutePath)
                    }
                }
            }
        }
    }

    fun shareLogBundle(context: Context, file: File) {
        if (!file.exists() || !file.isFile || !file.canRead()) {
            throw IllegalStateException("share file unavailable: ${file.absolutePath}")
        }
        val authority = context.packageName + ".files"
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = ZIP_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        val resolvedTargets = context.packageManager.queryIntentActivities(intent, 0)
        resolvedTargets.forEach { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName ?: return@forEach
            runCatching {
                context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure {
                XLog.w(
                    "grantUriPermission failed: pkg=%s uri=%s err=%s",
                    packageName,
                    uri.toString(),
                    it.message ?: it.javaClass.simpleName,
                )
            }
        }
        XLog.i(
            "shareLogBundle: file=%s size=%d uri=%s targets=%d",
            file.absolutePath,
            file.length(),
            uri.toString(),
            resolvedTargets.size,
        )
        val chooser = Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            clipData = intent.clipData
        }
        context.startActivity(chooser)
    }

    /**
     * Clear app log related directories (log/crash/exported logs).
     * Keeps directory nodes and removes files/sub-directories inside.
     */
    fun clearLogFolders(context: Context): ClearResult {
        synchronized(opLock) {
            val details = mutableListOf<String>()
            var success = true

            RuntimeLogStore.clear()

            val targets = listOf(
                "log" to StorageUtils.getLogDir(context),
                "crash" to StorageUtils.getCrashLogDir(context),
                "private_export" to getPrivateExportDir(context),
            )

            targets.forEach { (name, dir) ->
                if (dir == null) {
                    success = false
                    details += "$name unavailable"
                    return@forEach
                }
                val ok = clearDirectoryContents(dir)
                if (ok) {
                    details += "$name cleared"
                } else {
                    success = false
                    details += "$name clear failed"
                }
            }

            return ClearResult(success = success, details = details.joinToString("; "))
        }
    }

    private fun copyLsposedLogs(stagingDir: File, details: MutableList<String>): Boolean {
        val lsposedTargetRoot = File(stagingDir, "lsposed")
        var copied = false

        LSPOSED_LOG_DIRS.forEach { path ->
            val src = File(path)
            if (src.exists() && src.canRead()) {
                val target = File(lsposedTargetRoot, src.name)
                copyDirectory(src, target)
                details += "lsposed direct: $path"
                copied = true
            }
        }
        if (copied) return true

        val targetPath = lsposedTargetRoot.absolutePath
        val targetUid = runCatching { android.os.Process.myUid() }.getOrDefault(-1)
        val shellCmd = buildString {
            append("mkdir -p ${shQuote(targetPath)}; ")
            LSPOSED_LOG_DIRS.forEach { path ->
                val name = File(path).name
                append("if [ -d ${shQuote(path)} ]; then ")
                append("cp -R ${shQuote(path)} ${shQuote("$targetPath/")} && ")
                append("chmod -R a+rX ${shQuote("$targetPath/$name")} ; ")
                if (targetUid > 0) {
                    append("chown -R $targetUid:$targetUid ${shQuote("$targetPath/$name")} ; ")
                }
                append("fi; ")
            }
        }
        val suResult = runSuCommand(shellCmd)
        if (suResult.exitCode == 0) {
            val hasAny = lsposedTargetRoot.exists() &&
                (lsposedTargetRoot.listFiles()?.isNotEmpty() == true)
            if (hasAny) {
                details += "lsposed copied via su"
                return true
            }
        }
        details += "lsposed su failed: ${suResult.stderr.ifBlank { suResult.stdout }.ifBlank { "unknown" }}"
        return false
    }

    private data class ShellResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun runSuCommand(command: String): ShellResult = try {
        val process = ProcessBuilder("su", "-c", command).start()
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        ShellResult(exitCode, stdout, stderr)
    } catch (e: java.io.IOException) {
        ShellResult(-1, "", e.message ?: e.javaClass.simpleName)
    } catch (e: SecurityException) {
        ShellResult(-1, "", e.message ?: e.javaClass.simpleName)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        ShellResult(-1, "", e.message ?: e.javaClass.simpleName)
    }

    private fun copyDirectory(source: File, target: File) {
        source.walkTopDown().forEach { file ->
            val relative = file.relativeTo(source).path
            val dest = if (relative.isEmpty()) target else File(target, relative)
            if (file.isDirectory) {
                ensureDirectory(dest, recreateWhenFile = true)
            } else {
                if (!file.exists()) return@forEach
                val parent = dest.parentFile
                if (parent == null || !ensureDirectory(parent, recreateWhenFile = true)) {
                    XLog.w("Skip copy due to invalid parent dir: %s", dest.absolutePath)
                    return@forEach
                }
                runCatching {
                    file.copyTo(dest, overwrite = true)
                }.onFailure {
                    XLog.w(
                        "Skip copy file failed: src=%s dst=%s err=%s",
                        file.absolutePath,
                        dest.absolutePath,
                        it.message ?: it.javaClass.simpleName,
                    )
                }
            }
        }
    }

    private fun zipDirectory(sourceDir: File, outputZip: File) {
        FileOutputStream(outputZip).use { fos ->
            ZipOutputStream(fos).use { zos ->
                sourceDir.walkTopDown()
                    .filter { it.isFile }
                    .forEach { file ->
                        runCatching {
                            val entryName = file.relativeTo(sourceDir).invariantSeparatorsPath
                            zos.putNextEntry(ZipEntry(entryName))
                            file.inputStream().use { input -> input.copyTo(zos) }
                            zos.closeEntry()
                        }.onFailure {
                            XLog.w(
                                "Skip zipping unreadable file: %s err=%s",
                                file.absolutePath,
                                it.message ?: it.javaClass.simpleName,
                            )
                        }
                    }
            }
        }
    }

    private fun clearDirectoryContents(dir: File): Boolean {
        return runCatching {
            if (!ensureDirectory(dir, recreateWhenFile = true)) {
                return@runCatching false
            }
            var deletedAll = true
            dir.listFiles().orEmpty().forEach { child ->
                if (!deleteRecursivelyWithSuFallback(child)) {
                    deletedAll = false
                    XLog.w("Failed to delete log child: %s", child.absolutePath)
                }
            }
            deletedAll && ensureDirectory(dir, recreateWhenFile = true)
        }.getOrDefault(false)
    }

    private fun ensureDirectory(dir: File, recreateWhenFile: Boolean): Boolean {
        if (dir.exists()) {
            if (dir.isDirectory) return true
            if (!recreateWhenFile) return false
            if (!dir.delete()) {
                XLog.w("Failed to delete non-directory path: %s", dir.absolutePath)
                return false
            }
        }
        if (!dir.mkdirs() && !dir.exists()) {
            XLog.w("Failed to mkdirs for path: %s", dir.absolutePath)
            return false
        }
        return dir.isDirectory
    }

    private fun getPrivateExportDir(context: Context): File {
        return StorageUtils.getPrivateLogExportDir(context)
    }

    private fun deleteRecursivelyWithSuFallback(target: File): Boolean {
        if (!target.exists()) return true
        if (target.deleteRecursively()) return true
        val suResult = runSuCommand("rm -rf ${shQuote(target.absolutePath)}")
        val deleted = !target.exists()
        if (!deleted) {
            XLog.w(
                "su rm fallback failed: path=%s exit=%d stderr=%s stdout=%s",
                target.absolutePath,
                suResult.exitCode,
                suResult.stderr,
                suResult.stdout,
            )
        }
        return deleted
    }

    private fun shQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }
}
