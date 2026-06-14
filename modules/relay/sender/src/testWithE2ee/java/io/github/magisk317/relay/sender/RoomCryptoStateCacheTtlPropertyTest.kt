package io.github.magisk317.relay.sender

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property 2: 房间加密状态缓存 TTL 行为
 *
 * Feature: matrix-e2ee-support, Property 2: 房间加密状态缓存 TTL 行为
 *
 * **Validates: Requirements 1.6, 1.7**
 *
 * For any room ID with a cached encryption status entry, if the entry's age is less than
 * 60 minutes, subsequent queries SHALL return the cached value without network access.
 * If the entry's age is 60 minutes or greater, the next query SHALL trigger a fresh
 * network request and update the cache with the new result.
 */
class RoomCryptoStateCacheTtlPropertyTest : FunSpec({

    beforeEach {
        RoomCryptoState.clearCache()
    }

    afterEach {
        RoomCryptoState.clearCache()
    }

    // --- Generators ---

    // Generator for room IDs in Matrix format
    val roomIdArb: Arb<String> = arbitrary {
        val localpart = Arb.string(3..20).bind()
        val domain = Arb.element("example.com", "matrix.org", "server.io", "test.net").bind()
        "!$localpart:$domain"
    }

    // Generator for timestamps within TTL window (not expired)
    // Age is in [0, CACHE_TTL_MS - 1] — strictly less than TTL
    val freshOffsetArb: Arb<Long> = Arb.long(0L..RoomCryptoState.CACHE_TTL_MS - 1)

    // Generator for timestamps beyond TTL window (expired)
    // Age is in [CACHE_TTL_MS, CACHE_TTL_MS * 10] — at or beyond TTL
    val expiredOffsetArb: Arb<Long> = Arb.long(
        RoomCryptoState.CACHE_TTL_MS..RoomCryptoState.CACHE_TTL_MS * 10
    )

    // Generator for random encryption state
    val encryptedArb: Arb<Boolean> = Arb.boolean()

    // Generator for random algorithm strings
    val algorithmArb: Arb<String?> = arbitrary {
        val useAlg = Arb.boolean().bind()
        if (useAlg) {
            Arb.element(
                "m.megolm.v1.aes-sha2",
                "m.olm.v1.curve25519-aes-sha2",
                "custom.algorithm"
            ).bind()
        } else {
            null
        }
    }

    // --- Property Tests ---

    test("Property 2: entry within TTL window returns isExpired false cache hit") {
        /**
         * **Validates: Requirements 1.6**
         *
         * For any EncryptionInfo entry whose age (currentTime - queriedAt) is strictly
         * less than CACHE_TTL_MS, isExpired SHALL return false, indicating the cached
         * value should be reused.
         */
        checkAll(100, freshOffsetArb, encryptedArb, algorithmArb) { offset, encrypted, algorithm ->
            val now = System.currentTimeMillis()
            val info = RoomCryptoState.EncryptionInfo(
                encrypted = encrypted,
                algorithm = algorithm,
                queriedAt = now - offset
            )

            RoomCryptoState.isExpired(info) shouldBe false
        }
    }

    test("Property 2: entry older than TTL returns isExpired true cache miss") {
        /**
         * **Validates: Requirements 1.7**
         *
         * For any EncryptionInfo entry whose age (currentTime - queriedAt) is greater
         * than or equal to CACHE_TTL_MS, isExpired SHALL return true, indicating the
         * cache entry should be refreshed.
         */
        checkAll(100, expiredOffsetArb, encryptedArb, algorithmArb) { offset, encrypted, algorithm ->
            val now = System.currentTimeMillis()
            val info = RoomCryptoState.EncryptionInfo(
                encrypted = encrypted,
                algorithm = algorithm,
                queriedAt = now - offset
            )

            RoomCryptoState.isExpired(info) shouldBe true
        }
    }

    test("Property 2: boundary behavior exactly at TTL returns isExpired true") {
        /**
         * **Validates: Requirements 1.7**
         *
         * When the entry age is exactly equal to CACHE_TTL_MS, the entry SHALL be
         * considered expired (>= comparison). This verifies the boundary condition:
         * entries AT the TTL threshold are treated as expired.
         */
        checkAll(100, roomIdArb, encryptedArb, algorithmArb) { _, encrypted, algorithm ->
            val now = System.currentTimeMillis()
            val info = RoomCryptoState.EncryptionInfo(
                encrypted = encrypted,
                algorithm = algorithm,
                queriedAt = now - RoomCryptoState.CACHE_TTL_MS
            )

            RoomCryptoState.isExpired(info) shouldBe true
        }
    }

    test("Property 2: cache stores and retrieves entries by room ID correctly") {
        /**
         * **Validates: Requirements 1.6**
         *
         * For any room ID and any encryption info stored in the cache, if the entry
         * is not expired, it should be retrievable from the cache with the same values.
         * Different room IDs should maintain independent cache entries.
         */
        checkAll(100, roomIdArb, encryptedArb, algorithmArb) { roomId, encrypted, algorithm ->
            RoomCryptoState.clearCache()

            val info = RoomCryptoState.EncryptionInfo(
                encrypted = encrypted,
                algorithm = algorithm,
                queriedAt = System.currentTimeMillis()
            )

            // Store in cache
            RoomCryptoState.cache[roomId] = info

            // Retrieve and verify
            val cached = RoomCryptoState.cache[roomId]
            cached shouldBe info
            cached!!.encrypted shouldBe encrypted
            cached.algorithm shouldBe algorithm
            RoomCryptoState.isExpired(cached) shouldBe false
        }
    }
})
