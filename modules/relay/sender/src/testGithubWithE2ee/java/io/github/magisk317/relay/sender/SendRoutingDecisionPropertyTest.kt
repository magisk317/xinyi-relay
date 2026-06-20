@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package io.github.magisk317.relay.sender

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.boolean
import io.kotest.property.checkAll

/**
 * Property 8: 发送路由决策
 *
 * Feature: matrix-e2ee-support, Property 8: 发送路由决策
 *
 * **Validates: Requirements 5.1, 5.2, 8.2, 8.3, 8.4, 8.5**
 *
 * For any message send invocation with state (e2eeAvailable: Boolean, roomEncrypted: Boolean),
 * the routing decision SHALL be: use E2EE path if and only if both e2eeAvailable AND roomEncrypted
 * are true; otherwise use Plaintext path.
 *
 * Decision matrix:
 * - (e2eeAvailable=false, roomEncrypted=false) → plaintext send
 * - (e2eeAvailable=false, roomEncrypted=true)  → plaintext send
 * - (e2eeAvailable=true,  roomEncrypted=false) → plaintext send
 * - (e2eeAvailable=true,  roomEncrypted=true)  → encrypted send
 */
class SendRoutingDecisionPropertyTest : FunSpec({

    test("Property 8: routing decision is E2EE iff both e2eeAvailable AND roomEncrypted are true") {
        /**
         * **Validates: Requirements 5.1, 5.2, 8.2, 8.3, 8.4, 8.5**
         *
         * For any combination of (e2eeAvailable, roomEncrypted), the routing decision
         * must equal (e2eeAvailable AND roomEncrypted).
         */
        checkAll(PropTestConfig(iterations = 100), Arb.boolean(), Arb.boolean()) { e2eeAvailable, roomEncrypted ->
            val result = MatrixE2eeUtils.shouldUseE2ee(e2eeAvailable, roomEncrypted)
            val expected = e2eeAvailable && roomEncrypted
            result shouldBe expected
        }
    }

    test("Property 8: e2eeAvailable=false always routes to plaintext regardless of roomEncrypted") {
        /**
         * **Validates: Requirements 5.1, 8.3**
         *
         * When E2EE module is unavailable, all messages go through plaintext path
         * regardless of room encryption state.
         */
        checkAll(PropTestConfig(iterations = 100), Arb.boolean()) { roomEncrypted ->
            val result = MatrixE2eeUtils.shouldUseE2ee(
                e2eeAvailable = false,
                roomEncrypted = roomEncrypted,
            )
            result shouldBe false
        }
    }

    test("Property 8: roomEncrypted=false always routes to plaintext regardless of e2eeAvailable") {
        /**
         * **Validates: Requirements 5.2, 8.4**
         *
         * When the target room is not encrypted, messages go through plaintext path
         * regardless of E2EE module availability.
         */
        checkAll(PropTestConfig(iterations = 100), Arb.boolean()) { e2eeAvailable ->
            val result = MatrixE2eeUtils.shouldUseE2ee(
                e2eeAvailable = e2eeAvailable,
                roomEncrypted = false,
            )
            result shouldBe false
        }
    }

    test("Property 8: exhaustive truth table verification") {
        /**
         * **Validates: Requirements 5.1, 5.2, 8.2, 8.3, 8.4, 8.5**
         *
         * Exhaustively verify all four combinations of the routing decision.
         */
        // (false, false) → plaintext
        MatrixE2eeUtils.shouldUseE2ee(e2eeAvailable = false, roomEncrypted = false) shouldBe false
        // (false, true) → plaintext
        MatrixE2eeUtils.shouldUseE2ee(e2eeAvailable = false, roomEncrypted = true) shouldBe false
        // (true, false) → plaintext
        MatrixE2eeUtils.shouldUseE2ee(e2eeAvailable = true, roomEncrypted = false) shouldBe false
        // (true, true) → encrypted
        MatrixE2eeUtils.shouldUseE2ee(e2eeAvailable = true, roomEncrypted = true) shouldBe true
    }

    test("Property 8: decision is evaluated fresh on each invocation (not cached)") {
        /**
         * **Validates: Requirements 8.5**
         *
         * The routing decision is a pure function - calling it multiple times with
         * different inputs always yields the correct result for that input,
         * demonstrating no stale state is cached between invocations.
         */
        checkAll(PropTestConfig(iterations = 100), Arb.boolean(), Arb.boolean()) { e2eeAvailable, roomEncrypted ->
            // First call with one set of inputs
            val result1 = MatrixE2eeUtils.shouldUseE2ee(e2eeAvailable, roomEncrypted)
            // Second call with inverted inputs
            val result2 = MatrixE2eeUtils.shouldUseE2ee(!e2eeAvailable, !roomEncrypted)
            // Third call with original inputs should still give same result
            val result3 = MatrixE2eeUtils.shouldUseE2ee(e2eeAvailable, roomEncrypted)

            result1 shouldBe (e2eeAvailable && roomEncrypted)
            result2 shouldBe (!e2eeAvailable && !roomEncrypted)
            result3 shouldBe result1
        }
    }
})
