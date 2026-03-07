package com.github.magisk317.smscode.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object ApkSecurityVerifier {

    data class VerificationResult(
        val success: Boolean,
        val reason: String? = null,
    )

    fun verifyDownloadedApk(
        context: Context,
        apkFile: File,
        expectedSha256: String,
        expectedSigningCertSha256: String,
    ): VerificationResult {
        if (!apkFile.exists()) return VerificationResult(false, "apk_not_found")
        if (expectedSha256.isBlank()) return VerificationResult(false, "missing_sha256")
        if (expectedSigningCertSha256.isBlank()) return VerificationResult(false, "missing_signing_cert")

        val actualSha = computeSha256(apkFile)
        if (!isDigestMatch(actualSha, expectedSha256)) {
            return VerificationResult(false, "sha256_mismatch")
        }

        val archiveInfo = getArchivePackageInfo(context.packageManager, apkFile) ?: return VerificationResult(
            false,
            "archive_parse_failed",
        )
        if (archiveInfo.packageName != context.packageName) {
            return VerificationResult(false, "package_name_mismatch")
        }

        val currentSignatures = getInstalledSigningCertDigests(context.packageManager, context.packageName)
        val archiveSignatures = getArchiveSigningCertDigests(context.packageManager, apkFile)
        if (currentSignatures.isEmpty() || archiveSignatures.isEmpty()) {
            return VerificationResult(false, "signing_info_missing")
        }
        if (!isSigningCertExpected(archiveSignatures, expectedSigningCertSha256)) {
            return VerificationResult(false, "expected_signing_cert_mismatch")
        }
        if (currentSignatures.intersect(archiveSignatures).isEmpty()) {
            return VerificationResult(false, "installed_signing_cert_mismatch")
        }
        return VerificationResult(true)
    }

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    fun isDigestMatch(actual: String, expected: String): Boolean =
        normalizeDigest(actual) == normalizeDigest(expected)

    fun isSigningCertExpected(certs: Set<String>, expected: String): Boolean {
        val normalizedExpected = normalizeDigest(expected)
        return certs.any { normalizeDigest(it) == normalizedExpected }
    }

    private fun normalizeDigest(raw: String): String =
        raw.lowercase().replace("[^0-9a-f]".toRegex(), "")

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun getInstalledSigningCertDigests(
        pm: PackageManager,
        packageName: String,
    ): Set<String> {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }
        return extractSigningDigests(packageInfo)
    }

    private fun getArchiveSigningCertDigests(pm: PackageManager, apkFile: File): Set<String> {
        val packageInfo = getArchivePackageInfo(pm, apkFile) ?: return emptySet()
        return extractSigningDigests(packageInfo)
    }

    private fun getArchivePackageInfo(pm: PackageManager, apkFile: File): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        }
    }

    private fun extractSigningDigests(packageInfo: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }
        return signatures
            ?.mapNotNull { signature ->
                runCatching {
                    MessageDigest.getInstance("SHA-256")
                        .digest(signature.toByteArray())
                        .toHex()
                }.getOrNull()
            }
            ?.toSet()
            ?: emptySet()
    }
}
