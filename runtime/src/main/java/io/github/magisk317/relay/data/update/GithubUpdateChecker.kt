package io.github.magisk317.relay.data.update

import android.os.Build
import io.github.magisk317.relay.runtime.BuildConfig
import io.github.magisk317.smscode.runtime.common.update.GithubUpdateChecker as SharedGithubUpdateChecker
import io.github.magisk317.smscode.runtime.common.update.GithubUpdateConfig

object GithubUpdateChecker {

    private val config = GithubUpdateConfig(
        latestReleaseApiUrl = "https://api.github.com/repos/magisk317/xinyi-relay/releases/latest",
        defaultReleaseHtmlUrl = "https://github.com/magisk317/xinyi-relay/releases/latest",
        userAgent = "XinyiRelay",
    )

    suspend fun fetchLatestRelease(): GithubReleaseInfo? = SharedGithubUpdateChecker.fetchLatestRelease(config)

    suspend fun fetchUpgradeInfo(): UpgradeCheckResult = SharedGithubUpdateChecker.fetchUpgradeInfo(config)

    suspend fun fetchUpgradeInfoWithRequester(
        requestReleaseJson: suspend (String) -> String?,
    ): UpgradeCheckResult = SharedGithubUpdateChecker.fetchUpgradeInfoWithRequester(config, requestReleaseJson)

    suspend fun fetchLatestReleaseWithRequester(
        requestReleaseJson: suspend (String) -> String?,
    ): GithubReleaseInfo? = SharedGithubUpdateChecker.fetchLatestReleaseWithRequester(config, requestReleaseJson)

    fun isNewer(currentVersion: String, latestVersion: String): Boolean =
        SharedGithubUpdateChecker.isNewer(currentVersion, latestVersion)

    fun isNewer(currentVersionCode: Long, latestVersionCode: Long): Boolean =
        SharedGithubUpdateChecker.isNewer(currentVersionCode, latestVersionCode)

    fun parseUpgradeCheckResult(body: String): UpgradeCheckResult =
        SharedGithubUpdateChecker.parseUpgradeCheckResult(config, body)

    fun parseStructuredUpgradeJson(body: String): UpgradeInfo? =
        SharedGithubUpdateChecker.parseStructuredUpgradeJson(config, body)

    fun selectBestApkForDevice(
        apks: List<UpgradeApkAsset>,
        supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
        requiredXposedApiFlavor: String = BuildConfig.XPOSED_API_FLAVOR,
    ): UpgradeApkAsset? {
        return SharedGithubUpdateChecker.selectBestApkForDevice(
            apks = apks,
            supportedAbis = supportedAbis,
            requiredXposedApiFlavor = requiredXposedApiFlavor,
        )
    }

    fun parseLatestReleaseJson(body: String): GithubReleaseInfo? =
        SharedGithubUpdateChecker.parseLatestReleaseJson(config, body)
}
