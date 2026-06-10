package io.github.magisk317.relay.update

import android.content.Context
import io.github.magisk317.smscode.runtime.common.update.ApkSecurityVerifier as SharedApkSecurityVerifier
import java.io.File

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
        val result = SharedApkSecurityVerifier.verifyDownloadedApk(
            context = context,
            apkFile = apkFile,
            expectedSha256 = expectedSha256,
            expectedSigningCertSha256 = expectedSigningCertSha256,
        )
        return VerificationResult(
            success = result.success,
            reason = result.reason,
        )
    }

    fun computeSha256(file: File): String = SharedApkSecurityVerifier.computeSha256(file)

    fun isDigestMatch(actual: String, expected: String): Boolean =
        SharedApkSecurityVerifier.isDigestMatch(actual, expected)

    fun isSigningCertExpected(certs: Set<String>, expected: String): Boolean =
        SharedApkSecurityVerifier.isSigningCertExpected(certs, expected)
}
