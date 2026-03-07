package io.github.magisk317.relay.data.update

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.math.max

data class GithubReleaseInfo(
    val versionName: String,
    val htmlUrl: String,
)

object GithubUpdateChecker {

    private const val DEFAULT_RELEASE_HTML_URL =
        "https://github.com/magisk317/xinyi-relay/releases/latest"

    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/magisk317/xinyi-relay/releases/latest"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun fetchLatestRelease(): GithubReleaseInfo? = fetchLatestReleaseWithRequester { apiUrl ->
        requestReleaseJson(apiUrl)
    }

    suspend fun fetchUpgradeInfo(): UpgradeCheckResult = fetchUpgradeInfoWithRequester { apiUrl ->
        requestReleaseJson(apiUrl)
    }

    suspend fun fetchUpgradeInfoWithRequester(
        requestReleaseJson: suspend (String) -> String?,
    ): UpgradeCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val body = requestReleaseJson(LATEST_RELEASE_API)
                ?: return@runCatching UpgradeCheckResult.CheckFailed("empty_response")
            parseUpgradeCheckResult(body)
        }.getOrElse {
            UpgradeCheckResult.CheckFailed(it.message ?: it.javaClass.simpleName)
        }
    }

    suspend fun fetchLatestReleaseWithRequester(
        requestReleaseJson: suspend (String) -> String?,
    ): GithubReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val body = requestReleaseJson(LATEST_RELEASE_API) ?: return@runCatching null
            parseLatestReleaseJson(body)
        }.getOrNull()
    }

    private fun requestReleaseJson(apiUrl: String): String? {
        val request = Request.Builder()
            .url(apiUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "XinyiRelay")
            .get()
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body.string()
            }
        }.getOrNull()
    }

    fun isNewer(currentVersion: String, latestVersion: String): Boolean =
        compareVersions(currentVersion, latestVersion) < 0

    fun isNewer(currentVersionCode: Long, latestVersionCode: Long): Boolean =
        currentVersionCode < latestVersionCode

    fun parseUpgradeCheckResult(body: String): UpgradeCheckResult {
        parseStructuredUpgradeJson(body)?.let { return UpgradeCheckResult.Structured(it) }
        parseLatestReleaseJson(body)?.let { return UpgradeCheckResult.LegacyLink(it) }
        return UpgradeCheckResult.CheckFailed("invalid_payload")
    }

    fun parseStructuredUpgradeJson(body: String): UpgradeInfo? {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val hasStructuredMarker = root.containsKey("apks") ||
            root.containsKey("versionCode") ||
            root.containsKey("signingCertSha256") ||
            root.containsKey("signing_cert_sha256")
        if (!hasStructuredMarker) return null

        val versionName = root.stringOrBlank("versionName").ifBlank {
            root.stringOrBlank("tag_name").trim().removePrefix("v").removePrefix("V")
        }
        if (versionName.isBlank()) return null

        val versionCode = root.longOrZero("versionCode")
        val htmlUrl = root.stringOrBlank("htmlUrl")
            .ifBlank { root.stringOrBlank("html_url") }
            .ifBlank { DEFAULT_RELEASE_HTML_URL }
        val changelog = root.stringOrBlank("changelog")
        val signingCertSha256 = root.stringOrBlank("signingCertSha256")
            .ifBlank { root.stringOrBlank("signing_cert_sha256") }

        val versionLogs = root.arrayOrEmpty("versionLogs").mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            VersionLog(
                name = obj.stringOrBlank("name"),
                code = obj.longOrZero("code"),
                desc = obj.stringOrBlank("desc"),
            )
        }

        val apks = root.arrayOrEmpty("apks").mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val rawUrl = obj.stringOrBlank("downloadUrl")
                .ifBlank { obj.stringOrBlank("download_url") }
            val resolvedUrl = resolveUrl(LATEST_RELEASE_API, rawUrl)
            if (resolvedUrl.isBlank()) return@mapNotNull null
            UpgradeApkAsset(
                abi = obj.stringOrBlank("abi"),
                downloadUrl = resolvedUrl,
                fileSize = obj.longOrZero("fileSize").takeIf { it > 0L } ?: obj.longOrZero("size"),
                sha256 = obj.stringOrBlank("sha256"),
            )
        }

        return UpgradeInfo(
            versionCode = versionCode,
            versionName = versionName,
            htmlUrl = htmlUrl,
            changelog = changelog,
            versionLogs = versionLogs,
            apks = apks,
            signingCertSha256 = signingCertSha256,
        )
    }

    fun selectBestApkForDevice(
        apks: List<UpgradeApkAsset>,
        supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
    ): UpgradeApkAsset? {
        if (apks.isEmpty()) return null
        val indexed = apks.associateBy { normalizeAbi(it.abi) }

        for (abi in supportedAbis) {
            val normalized = normalizeAbi(abi)
            indexed[normalized]?.let { return it }
            if (normalized == "armeabi") {
                indexed["armeabi-v7a"]?.let { return it }
            }
        }

        indexed["universal"]?.let { return it }
        indexed["all"]?.let { return it }
        indexed["any"]?.let { return it }
        indexed["*"]?.let { return it }
        indexed[""]?.let { return it }
        return apks.firstOrNull()
    }

    fun parseLatestReleaseJson(body: String): GithubReleaseInfo? {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() ?: return null
        if (root !is JsonObject) return null

        val rawTag = root.stringOrBlank("tag_name")
        val normalizedVersion = rawTag.trim().removePrefix("v").removePrefix("V")
        if (normalizedVersion.isBlank()) return null
        val htmlUrl = root.stringOrBlank("html_url").ifBlank { DEFAULT_RELEASE_HTML_URL }
        return GithubReleaseInfo(normalizedVersion, htmlUrl)
    }

    private fun JsonObject.stringOrBlank(key: String): String =
        runCatching { (this[key] ?: return "").toUnquotedString() }.getOrDefault("")

    private fun JsonObject.arrayOrEmpty(key: String): JsonArray =
        this[key] as? JsonArray ?: JsonArray(emptyList())

    private fun JsonObject.longOrZero(key: String): Long {
        val raw = stringOrBlank(key)
        if (raw.isBlank()) return 0L
        return raw.toLongOrNull() ?: 0L
    }

    private fun JsonElement.toUnquotedString(): String {
        val raw = toString()
        return if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            raw.substring(1, raw.length - 1)
        } else {
            raw
        }
    }

    private fun normalizeAbi(abi: String): String = abi.trim().lowercase()

    private fun resolveUrl(baseUrl: String, raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        return runCatching {
            URI(baseUrl).resolve(trimmed).toString()
        }.getOrDefault("")
    }

    private fun compareVersions(currentVersion: String, latestVersion: String): Int {
        val currentParts = extractVersionParts(currentVersion)
        val latestParts = extractVersionParts(latestVersion)
        val length = max(currentParts.size, latestParts.size)
        for (index in 0 until length) {
            val current = if (index < currentParts.size) currentParts[index] else 0
            val latest = if (index < latestParts.size) latestParts[index] else 0
            if (current < latest) return -1
            if (current > latest) return 1
        }
        return 0
    }

    private fun extractVersionParts(version: String): List<Int> =
        VERSION_PART_REGEX.findAll(version)
            .mapNotNull { part -> part.value.toIntOrNull() }
            .toList()

    private val VERSION_PART_REGEX = Regex("\\d+")
}
