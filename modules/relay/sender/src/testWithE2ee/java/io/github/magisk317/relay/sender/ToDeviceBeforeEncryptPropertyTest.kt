package io.github.magisk317.relay.sender

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.shuffle
import io.kotest.property.checkAll

/**
 * Property 10: to-device 事件处理先于加密操作
 *
 * Feature: matrix-e2ee-support, Property 10: to-device 事件处理先于加密操作
 *
 * **Validates: Requirements 9.4**
 *
 * For any encrypted message send operation, the to-device event polling and processing step
 * SHALL complete before the message encryption step begins, ensuring the latest session state
 * is used for encryption.
 *
 * Test approach:
 * - The sendEncrypted() method in MatrixE2eeUtils follows a strict ordering:
 *   1. ensureKeySyncReady(client) — polls /sync for to-device events, processes them
 *   2. room.sendRaw() — encrypts and sends the message (SDK handles Megolm encryption internally)
 * - We test the ordering contract via:
 *   a. verifySendOperationOrder() — validates that a given operation sequence satisfies the
 *      "sync before encrypt" constraint
 *   b. getCanonicalSendOperationOrder() — returns the ground-truth ordering used by sendEncrypted()
 * - Property: for any permutation of operations, only sequences with "sync_to_device" before
 *   "encrypt_and_send" satisfy the ordering constraint; and the canonical order always satisfies it.
 */
class ToDeviceBeforeEncryptPropertyTest : FunSpec({

    test("Property 10: canonical operation order always satisfies sync-before-encrypt constraint") {
        /**
         * **Validates: Requirements 9.4**
         *
         * The canonical operation order (as implemented in sendEncrypted) must always
         * place sync_to_device before encrypt_and_send.
         */
        checkAll(PropTestConfig(iterations = 100), Arb.int(0..100)) {
            // For any invocation, the canonical order must satisfy the constraint
            val canonicalOrder = MatrixE2eeUtils.getCanonicalSendOperationOrder()
            MatrixE2eeUtils.verifySendOperationOrder(canonicalOrder) shouldBe true
        }
    }

    test("Property 10: sync_to_device appears at index 0 in canonical order (before encrypt)") {
        /**
         * **Validates: Requirements 9.4**
         *
         * The canonical order must have sync_to_device as the first operation,
         * ensuring to-device events are processed before any encryption begins.
         */
        checkAll(PropTestConfig(iterations = 100), Arb.int(0..100)) {
            val order = MatrixE2eeUtils.getCanonicalSendOperationOrder()
            order.indexOf("sync_to_device") shouldBe 0
            order.indexOf("encrypt_and_send") shouldBe 1
        }
    }

    test("Property 10: reversed order (encrypt before sync) always violates the constraint") {
        /**
         * **Validates: Requirements 9.4**
         *
         * If the operations were reordered so that encrypt_and_send comes before
         * sync_to_device, the ordering constraint must be violated. This demonstrates
         * that the verifier correctly rejects incorrect orderings.
         */
        checkAll(PropTestConfig(iterations = 100), Arb.int(0..100)) {
            val reversedOrder = listOf("encrypt_and_send", "sync_to_device")
            MatrixE2eeUtils.verifySendOperationOrder(reversedOrder) shouldBe false
        }
    }

    test("Property 10: any operation list with sync before encrypt satisfies the constraint") {
        /**
         * **Validates: Requirements 9.4**
         *
         * For any randomly generated list of operation names that includes both
         * "sync_to_device" and "encrypt_and_send" with sync appearing first,
         * the ordering constraint must be satisfied.
         */
        val operationListArb: Arb<List<String>> = arbitrary {
            val extraOps = Arb.list(
                Arb.element("init_client", "build_content", "get_room", "log_info"),
                0..5
            ).bind()
            // Always construct a list where sync comes before encrypt
            val beforeSync = extraOps.take(extraOps.size / 2)
            val afterSync = extraOps.drop(extraOps.size / 2)
            beforeSync + "sync_to_device" + afterSync + "encrypt_and_send"
        }

        checkAll(PropTestConfig(iterations = 100), operationListArb) { operations ->
            MatrixE2eeUtils.verifySendOperationOrder(operations) shouldBe true
        }
    }

    test("Property 10: any operation list with encrypt before sync violates the constraint") {
        /**
         * **Validates: Requirements 9.4**
         *
         * For any randomly generated list where "encrypt_and_send" appears before
         * "sync_to_device", the ordering constraint must be violated.
         */
        val badOrderListArb: Arb<List<String>> = arbitrary {
            val extraOps = Arb.list(
                Arb.element("init_client", "build_content", "get_room", "log_info"),
                0..5
            ).bind()
            // Construct a list where encrypt comes before sync (wrong order)
            val beforeEncrypt = extraOps.take(extraOps.size / 2)
            val afterEncrypt = extraOps.drop(extraOps.size / 2)
            beforeEncrypt + "encrypt_and_send" + afterEncrypt + "sync_to_device"
        }

        checkAll(PropTestConfig(iterations = 100), badOrderListArb) { operations ->
            MatrixE2eeUtils.verifySendOperationOrder(operations) shouldBe false
        }
    }

    test("Property 10: missing operations always violate the constraint") {
        /**
         * **Validates: Requirements 9.4**
         *
         * If either "sync_to_device" or "encrypt_and_send" is missing from the
         * operation list, the constraint cannot be satisfied. Both operations
         * are required for a valid E2EE send pipeline.
         */
        val missingOpsArb: Arb<List<String>> = arbitrary {
            val variant = Arb.element("missing_sync", "missing_encrypt", "missing_both").bind()
            val filler = Arb.list(
                Arb.element("init_client", "build_content", "get_room"),
                1..5
            ).bind()
            when (variant) {
                "missing_sync" -> filler + "encrypt_and_send"
                "missing_encrypt" -> filler + "sync_to_device"
                else -> filler
            }
        }

        checkAll(PropTestConfig(iterations = 100), missingOpsArb) { operations ->
            MatrixE2eeUtils.verifySendOperationOrder(operations) shouldBe false
        }
    }

    test("Matrix E2EE serialized send order keeps sync, encrypt, and post-send sync inside the lock") {
        checkAll(PropTestConfig(iterations = 100), Arb.int(0..100)) {
            val order = MatrixE2eeUtils.getCanonicalSerializedSendOperationOrder()
            MatrixE2eeUtils.verifySerializedSendOperationOrder(order) shouldBe true
            order.indexOf("acquire_send_lock") shouldBe 0
            order.indexOf("release_send_lock") shouldBe order.lastIndex
            order.indexOf("sync_to_device") shouldBe 1
            order.indexOf("encrypt_and_send") shouldBe 2
            order.indexOf("post_send_sync") shouldBe 3
        }
    }

    test("Matrix E2EE serialized send order rejects encrypting before the lock is acquired") {
        checkAll(PropTestConfig(iterations = 100), Arb.int(0..100)) {
            val badOrder = listOf(
                "encrypt_and_send",
                "acquire_send_lock",
                "sync_to_device",
                "post_send_sync",
                "release_send_lock",
            )
            MatrixE2eeUtils.verifySerializedSendOperationOrder(badOrder) shouldBe false
        }
    }
})
