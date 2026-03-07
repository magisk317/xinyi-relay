package com.github.magisk317.smscode.data.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ApkSecurityVerifierTest {

    @Test
    fun computeSha256_matchesKnownValue() {
        val file = File.createTempFile("sha", ".txt")
        file.writeText("abc")
        file.deleteOnExit()

        val digest = ApkSecurityVerifier.computeSha256(file)

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", digest)
    }

    @Test
    fun isDigestMatch_allowsColonAndCaseDifference() {
        assertTrue(
            ApkSecurityVerifier.isDigestMatch(
                "AA:BB:CC",
                "aabbcc",
            ),
        )
        assertFalse(ApkSecurityVerifier.isDigestMatch("aabbcd", "aabbcc"))
    }

    @Test
    fun isSigningCertExpected_matchesNormalizedFingerprint() {
        val certs = setOf("11:22:33", "AA:BB:CC")
        assertTrue(ApkSecurityVerifier.isSigningCertExpected(certs, "aabbcc"))
        assertFalse(ApkSecurityVerifier.isSigningCertExpected(certs, "ddeeff"))
    }
}
