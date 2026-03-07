package com.github.magisk317.smscode.data.update

import kotlinx.serialization.Serializable

@Serializable
data class VersionLog(
    val name: String = "",
    val code: Long = 0L,
    val desc: String = "",
)

@Serializable
data class UpgradeApkAsset(
    val abi: String = "",
    val downloadUrl: String = "",
    val fileSize: Long = 0L,
    val sha256: String = "",
)

@Serializable
data class UpgradeInfo(
    val versionCode: Long = 0L,
    val versionName: String = "",
    val htmlUrl: String = "",
    val changelog: String = "",
    val versionLogs: List<VersionLog> = emptyList(),
    val apks: List<UpgradeApkAsset> = emptyList(),
    val signingCertSha256: String = "",
)

sealed class UpgradeCheckResult {
    data class Structured(val info: UpgradeInfo) : UpgradeCheckResult()
    data class LegacyLink(val release: GithubReleaseInfo) : UpgradeCheckResult()
    data object NoUpdate : UpgradeCheckResult()
    data class CheckFailed(val message: String? = null) : UpgradeCheckResult()
}
