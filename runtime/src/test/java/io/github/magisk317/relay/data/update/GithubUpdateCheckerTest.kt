package io.github.magisk317.relay.data.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class GithubUpdateCheckerTest {

    @Test
    fun parseLatestReleaseJson_parsesVersionAndUrl() {
        val json = """{"tag_name":"v3.1.1","html_url":"https://github.com/magisk317/xinyi-relay/releases/tag/v3.1.1"}"""

        val release = GithubUpdateChecker.parseLatestReleaseJson(json)

        assertNotNull(release)
        assertEquals("3.1.1", release?.versionName)
        assertEquals(
            "https://github.com/magisk317/xinyi-relay/releases/tag/v3.1.1",
            release?.htmlUrl,
        )
    }

    @Test
    fun parseLatestReleaseJson_usesFallbackUrlWhenMissing() {
        val json = """{"tag_name":"V3.1.1"}"""

        val release = GithubUpdateChecker.parseLatestReleaseJson(json)

        assertNotNull(release)
        assertEquals("3.1.1", release?.versionName)
        assertEquals("https://github.com/magisk317/xinyi-relay/releases/latest", release?.htmlUrl)
    }

    @Test
    fun parseLatestReleaseJson_returnsNullWhenTagMissing() {
        val json = """{"name":"release-without-tag"}"""

        val release = GithubUpdateChecker.parseLatestReleaseJson(json)

        assertNull(release)
    }

    @Test
    fun isNewer_comparesSemanticLikeVersions() {
        assertTrue(GithubUpdateChecker.isNewer("3.1.0", "3.1.1"))
        assertFalse(GithubUpdateChecker.isNewer("3.1.1", "3.1.1"))
        assertFalse(GithubUpdateChecker.isNewer("3.1.2", "3.1.1"))
        assertTrue(GithubUpdateChecker.isNewer("3.1", "3.1.1"))
        assertTrue(GithubUpdateChecker.isNewer("v3.1.0", "V3.2.0-beta1"))
    }

    @Test
    fun fetchLatestReleaseWithRequester_returnsNullOnRequesterFailure() {
        val release = kotlinx.coroutines.runBlocking {
            GithubUpdateChecker.fetchLatestReleaseWithRequester {
                throw IOException("network down")
            }
        }
        assertNull(release)
    }

    @Test
    fun fetchLatestReleaseWithRequester_returnsNullOnMissingTag() {
        val release = kotlinx.coroutines.runBlocking {
            GithubUpdateChecker.fetchLatestReleaseWithRequester {
                """{"name":"release-without-tag"}"""
            }
        }
        assertNull(release)
    }

    @Test
    fun fetchLatestReleaseWithRequester_returnsParsedReleaseOnSuccess() {
        val release = kotlinx.coroutines.runBlocking {
            GithubUpdateChecker.fetchLatestReleaseWithRequester {
                """{"tag_name":"v9.9.9","html_url":"https://github.com/magisk317/xinyi-relay/releases/tag/v9.9.9"}"""
            }
        }
        assertNotNull(release)
        assertEquals("9.9.9", release?.versionName)
    }

    @Test
    fun parseStructuredUpgradeJson_parsesNewFormat() {
        val json = """
            {
              "versionCode": 31800,
              "versionName": "3.1.8",
              "htmlUrl": "https://github.com/magisk317/xinyi-relay/releases/tag/v3.1.8",
              "changelog": "fixes",
              "versionLogs": [{"name":"3.1.8","code":31800,"desc":"line1"}],
              "apks": [
                {"abi":"arm64-v8a","downloadUrl":"https://example.com/arm64_api101_release.apk","fileSize":100,"sha256":"abcd","xposedApiFlavor":"api101"},
                {"abi":"universal","downloadUrl":"https://example.com/universal_legacy_release.apk","fileSize":200,"sha256":"efgh"}
              ],
              "signingCertSha256": "AA:BB"
            }
        """.trimIndent()

        val info = GithubUpdateChecker.parseStructuredUpgradeJson(json)

        assertNotNull(info)
        assertEquals(31800L, info?.versionCode)
        assertEquals("3.1.8", info?.versionName)
        assertEquals(2, info?.apks?.size)
        assertEquals("AA:BB", info?.signingCertSha256)
        assertEquals("api101", info?.apks?.firstOrNull()?.xposedApiFlavor)
        assertEquals("legacy", info?.apks?.getOrNull(1)?.xposedApiFlavor)
    }

    @Test
    fun parseUpgradeCheckResult_fallsBackToReleaseLink() {
        val json = """{"tag_name":"v3.1.9","html_url":"https://example.com/release"}"""
        val result = GithubUpdateChecker.parseUpgradeCheckResult(json)
        val releaseLink = result as? UpgradeCheckResult.ReleaseLink
        assertNotNull(releaseLink)
        assertEquals("3.1.9", releaseLink?.release?.versionName)
    }

    @Test
    fun selectBestApkForDevice_prefersExactAbiWithinMatchingFlavor() {
        val apks = listOf(
            UpgradeApkAsset(abi = "arm64-v8a", downloadUrl = "legacy-arm64", xposedApiFlavor = "legacy"),
            UpgradeApkAsset(abi = "arm64-v8a", downloadUrl = "api101-arm64", xposedApiFlavor = "api101"),
            UpgradeApkAsset(abi = "universal", downloadUrl = "legacy-universal", xposedApiFlavor = "legacy"),
            UpgradeApkAsset(abi = "universal", downloadUrl = "api101-universal", xposedApiFlavor = "api101"),
        )

        val arm64 = GithubUpdateChecker.selectBestApkForDevice(
            apks = apks,
            supportedAbis = listOf("arm64-v8a"),
            requiredXposedApiFlavor = "legacy",
        )
        assertEquals("legacy-arm64", arm64?.downloadUrl)

        val x86 = GithubUpdateChecker.selectBestApkForDevice(
            apks = apks,
            supportedAbis = listOf("x86_64"),
            requiredXposedApiFlavor = "api101",
        )
        assertEquals("api101-universal", x86?.downloadUrl)
    }

    @Test
    fun selectBestApkForDevice_fallsBackToUntaggedUniversalWhenFlavorSpecificMissing() {
        val apks = listOf(
            UpgradeApkAsset(abi = "arm64-v8a", downloadUrl = "api101-arm64", xposedApiFlavor = "api101"),
            UpgradeApkAsset(abi = "universal", downloadUrl = "untagged-universal"),
        )

        val selected = GithubUpdateChecker.selectBestApkForDevice(
            apks = apks,
            supportedAbis = listOf("x86_64"),
            requiredXposedApiFlavor = "legacy",
        )

        assertEquals("untagged-universal", selected?.downloadUrl)
    }
}
