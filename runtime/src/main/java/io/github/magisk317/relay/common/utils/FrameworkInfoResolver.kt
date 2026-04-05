package io.github.magisk317.relay.common.utils

import android.content.Context
import io.github.magisk317.relay.diagnostics.ActivationDiagnosticsStore

data class FrameworkInfo(
    val name: String,
    val version: String,
    val moduleId: String? = null,
    val author: String? = null,
    val source: Source,
) {
    enum class Source {
        MODULE_PROP,
        XPOSED_SERVICE,
    }

    val displayName: String
        get() = name.ifBlank { "unknown" }

    val displayVersion: String
        get() = version.ifBlank { "unknown" }

    val displayLabel: String
        get() = if (displayVersion.equals("unknown", ignoreCase = true)) {
            displayName
        } else {
            "$displayName $displayVersion"
        }
}

object FrameworkInfoResolver {
    private val modulePropCandidates = listOf(
        "/data/adb/modules/zygisk_vector/module.prop",
        "/data/adb/modules/zygisk_lsposed/module.prop",
        "/data/adb/modules/riru_lsposed/module.prop",
        "/data/adb/modules/lsposed/module.prop",
    )

    fun resolve(context: Context): FrameworkInfo? {
        return resolveInstalledModuleInfo() ?: resolveFromActivationSnapshot(context)
    }

    fun resolveInstalledModuleInfo(): FrameworkInfo? {
        return modulePropCandidates.firstNotNullOfOrNull { path ->
            readModuleProp(path)?.let { parseModuleProp(it, path) }
        }
    }

    private fun resolveFromActivationSnapshot(context: Context): FrameworkInfo? {
        val snapshot = ActivationDiagnosticsStore.snapshot(context)
        val frameworkName = snapshot.lastServiceFrameworkName.trim()
        val frameworkVersion = snapshot.lastServiceFrameworkVersion.trim()
        val hasUsableName = frameworkName.isNotBlank() && !frameworkName.equals("unknown", ignoreCase = true)
        val hasUsableVersion = frameworkVersion.isNotBlank() && !frameworkVersion.equals("unknown", ignoreCase = true)
        if (!hasUsableName && !hasUsableVersion) {
            return null
        }
        return FrameworkInfo(
            name = frameworkName.ifBlank { "unknown" },
            version = frameworkVersion.ifBlank { "unknown" },
            source = FrameworkInfo.Source.XPOSED_SERVICE,
        )
    }

    internal fun parseModuleProp(content: String, path: String): FrameworkInfo? {
        val lines = content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        val moduleId = lines.firstOrNull { it.startsWith("id=") }
            ?.substringAfter("id=")
            ?.trim()
        val name = lines.firstOrNull { it.startsWith("name=") }
            ?.substringAfter("name=")
            ?.trim()
        val rawVersion = lines.firstOrNull { it.startsWith("version=") }
            ?.substringAfter("version=")
            ?.trim()
        val versionCode = lines.firstOrNull { it.startsWith("versionCode=") }
            ?.substringAfter("versionCode=")
            ?.trim()
        val author = lines.firstOrNull { it.startsWith("author=") }
            ?.substringAfter("author=")
            ?.trim()
        val version = formatVersion(rawVersion, versionCode) ?: return null
        val displayName = name
            ?.takeIf { it.isNotBlank() }
            ?: moduleId
            ?.takeIf { it.isNotBlank() }
            ?: path.substringAfterLast('/').ifBlank { return null }
        return FrameworkInfo(
            name = displayName,
            version = version,
            moduleId = moduleId,
            author = author,
            source = FrameworkInfo.Source.MODULE_PROP,
        )
    }

    private fun formatVersion(rawVersion: String?, versionCode: String?): String? {
        val normalized = rawVersion?.removePrefix("v")?.trim()
        return when {
            !normalized.isNullOrBlank() && !versionCode.isNullOrBlank() && !normalized.contains("(") ->
                "$normalized ($versionCode)"

            !normalized.isNullOrBlank() -> normalized

            !versionCode.isNullOrBlank() -> versionCode

            else -> null
        }
    }

    private fun readModuleProp(path: String): String? = try {
        val process = ProcessBuilder("su", "-c", "cat $path").start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode == 0 && output.isNotBlank()) output else null
    } catch (_: Exception) {
        null
    }
}
