package com.github.magisk317.smscode.data.update

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
                {"abi":"arm64-v8a","downloadUrl":"https://example.com/app-arm64.apk","fileSize":100,"sha256":"abcd"},
                {"abi":"universal","downloadUrl":"https://example.com/app-universal.apk","fileSize":200,"sha256":"efgh"}
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
    }

    @Test
    fun parseUpgradeCheckResult_fallsBackToLegacy() {
        val json = """{"tag_name":"v3.1.9","html_url":"https://example.com/release"}"""
        val result = GithubUpdateChecker.parseUpgradeCheckResult(json)
        val legacy = result as? UpgradeCheckResult.LegacyLink
        assertNotNull(legacy)
        assertEquals("3.1.9", legacy?.release?.versionName)
    }

    @Test
    fun selectBestApkForDevice_prefersExactAbiThenUniversal() {
        val apks = listOf(
            UpgradeApkAsset(abi = "arm64-v8a", downloadUrl = "a"),
            UpgradeApkAsset(abi = "universal", downloadUrl = "u"),
        )

        val arm64 = GithubUpdateChecker.selectBestApkForDevice(
            apks = apks,
            supportedAbis = listOf("arm64-v8a"),
        )
        assertEquals("a", arm64?.downloadUrl)

        val x86 = GithubUpdateChecker.selectBestApkForDevice(
            apks = apks,
            supportedAbis = listOf("x86_64"),
        )
        assertEquals("u", x86?.downloadUrl)
    }
}
