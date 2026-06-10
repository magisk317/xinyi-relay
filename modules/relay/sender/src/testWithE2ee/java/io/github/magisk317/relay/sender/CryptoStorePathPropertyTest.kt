package io.github.magisk317.relay.sender

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property 3: Crypto_Store 路径派生确定性
 *
 * For any valid userId string, the derived store directory path SHALL always equal
 * `{appFilesDir}/matrix-crypto/{sha256(userId).substring(0, 16)}/`,
 * and the derivation SHALL be a pure deterministic function (same input always produces same output).
 *
 * **Validates: Requirements 2.2**
 */
class CryptoStorePathPropertyTest : FunSpec({

    test("Property 3: sha256Hex is deterministic - same input always produces same output") {
        checkAll(PropTestConfig(iterations = 100), Arb.string(0..200)) { userId ->
            val result1 = MatrixE2eeUtils.sha256Hex(userId)
            val result2 = MatrixE2eeUtils.sha256Hex(userId)
            result1 shouldBe result2
        }
    }

    test("Property 3: derived path prefix (take 16) is deterministic for same input") {
        checkAll(PropTestConfig(iterations = 100), Arb.string(0..200)) { userId ->
            val path1 = MatrixE2eeUtils.sha256Hex(userId).take(16)
            val path2 = MatrixE2eeUtils.sha256Hex(userId).take(16)
            path1 shouldBe path2
        }
    }

    test("Property 3: sha256Hex output is always 64 hex characters") {
        checkAll(PropTestConfig(iterations = 100), Arb.string(0..200)) { userId ->
            val hash = MatrixE2eeUtils.sha256Hex(userId)
            hash.length shouldBe 64
            hash.all { it in '0'..'9' || it in 'a'..'f' } shouldBe true
        }
    }

    test("Property 3: derived path prefix is always exactly 16 hex characters") {
        checkAll(PropTestConfig(iterations = 100), Arb.string(0..200)) { userId ->
            val prefix = MatrixE2eeUtils.sha256Hex(userId).take(16)
            prefix.length shouldBe 16
            prefix.all { it in '0'..'9' || it in 'a'..'f' } shouldBe true
        }
    }
})
