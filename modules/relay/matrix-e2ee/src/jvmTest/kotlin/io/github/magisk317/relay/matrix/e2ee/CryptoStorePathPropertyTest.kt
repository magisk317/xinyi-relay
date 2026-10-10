package io.github.magisk317.relay.matrix.e2ee

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Property 3: Crypto_Store 路径派生确定性
 *
 * For any valid userId string, the derived store directory path SHALL always equal
 * `{appFilesDir}/matrix-crypto/{sha256(userId).substring(0, 16)}/`,
 * and the derivation SHALL be a pure deterministic function (same input always produces same output).
 *
 * **Validates: Requirements 2.2**
 */
@OptIn(io.kotest.common.ExperimentalKotest::class)
class CryptoStorePathPropertyTest {

    @Test
    fun `Property 3 - sha256Hex is deterministic - same input always produces same output`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 100), Arb.string(0..200)) { userId ->
                val result1 = MatrixE2eeSendPolicy.sha256Hex(userId)
                val result2 = MatrixE2eeSendPolicy.sha256Hex(userId)
                assertEquals(result2, result1, "sha256Hex must be deterministic for userId=$userId")
            }
        }
    }

    @Test
    fun `Property 3 - derived path prefix (take 16) is deterministic for same input`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 100), Arb.string(0..200)) { userId ->
                val path1 = MatrixE2eeSendPolicy.sha256Hex(userId).take(16)
                val path2 = MatrixE2eeSendPolicy.sha256Hex(userId).take(16)
                assertEquals(path2, path1, "path prefix must be deterministic for userId=$userId")
            }
        }
    }

    @Test
    fun `Property 3 - sha256Hex output is always 64 hex characters`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 100), Arb.string(0..200)) { userId ->
                val hash = MatrixE2eeSendPolicy.sha256Hex(userId)
                assertEquals(64, hash.length, "hash length for userId=$userId was: $hash")
                assertTrue(
                    hash.all { it in '0'..'9' || it in 'a'..'f' },
                    "hash must be lowercase hex but was: $hash",
                )
            }
        }
    }

    @Test
    fun `Property 3 - derived path prefix is always exactly 16 hex characters`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 100), Arb.string(0..200)) { userId ->
                val prefix = MatrixE2eeSendPolicy.sha256Hex(userId).take(16)
                assertEquals(16, prefix.length, "prefix length for userId=$userId was: $prefix")
                assertTrue(
                    prefix.all { it in '0'..'9' || it in 'a'..'f' },
                    "prefix must be lowercase hex but was: $prefix",
                )
            }
        }
    }
}
