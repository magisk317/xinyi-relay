@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package io.github.magisk317.relay.matrix.e2ee

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.boolean
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
class SendRoutingDecisionPropertyTest {

    /**
     * **Validates: Requirements 5.1, 5.2, 8.2, 8.3, 8.4, 8.5**
     *
     * For any combination of (e2eeAvailable, roomEncrypted), the routing decision
     * must equal (e2eeAvailable AND roomEncrypted).
     */
    @Test
    fun `Property 8 - routing decision is E2EE iff both e2eeAvailable AND roomEncrypted are true`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 100), Arb.boolean(), Arb.boolean()) { e2eeAvailable, roomEncrypted ->
                val result = MatrixE2eeSendPolicy.shouldUseE2ee(e2eeAvailable, roomEncrypted)
                val expected = e2eeAvailable && roomEncrypted
                assertEquals(
                    expected,
                    result,
                    "e2eeAvailable=$e2eeAvailable, roomEncrypted=$roomEncrypted",
                )
            }
        }
    }

    /**
     * **Validates: Requirements 5.1, 8.3**
     *
     * When E2EE module is unavailable, all messages go through plaintext path
     * regardless of room encryption state.
     */
    @Test
    fun `Property 8 - e2eeAvailable=false always routes to plaintext regardless of roomEncrypted`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 100), Arb.boolean()) { roomEncrypted ->
                val result = MatrixE2eeSendPolicy.shouldUseE2ee(
                    e2eeAvailable = false,
                    roomEncrypted = roomEncrypted,
                )
                assertFalse(result, "e2eeAvailable=false, roomEncrypted=$roomEncrypted must route to plaintext")
            }
        }
    }

    /**
     * **Validates: Requirements 5.2, 8.4**
     *
     * When the target room is not encrypted, messages go through plaintext path
     * regardless of E2EE module availability.
     */
    @Test
    fun `Property 8 - roomEncrypted=false always routes to plaintext regardless of e2eeAvailable`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 100), Arb.boolean()) { e2eeAvailable ->
                val result = MatrixE2eeSendPolicy.shouldUseE2ee(
                    e2eeAvailable = e2eeAvailable,
                    roomEncrypted = false,
                )
                assertFalse(result, "e2eeAvailable=$e2eeAvailable, roomEncrypted=false must route to plaintext")
            }
        }
    }

    /**
     * **Validates: Requirements 5.1, 5.2, 8.2, 8.3, 8.4, 8.5**
     *
     * Exhaustively verify all four combinations of the routing decision.
     */
    @Test
    fun `Property 8 - exhaustive truth table verification`() {
        // (false, false) → plaintext
        assertFalse(MatrixE2eeSendPolicy.shouldUseE2ee(e2eeAvailable = false, roomEncrypted = false))
        // (false, true) → plaintext
        assertFalse(MatrixE2eeSendPolicy.shouldUseE2ee(e2eeAvailable = false, roomEncrypted = true))
        // (true, false) → plaintext
        assertFalse(MatrixE2eeSendPolicy.shouldUseE2ee(e2eeAvailable = true, roomEncrypted = false))
        // (true, true) → encrypted
        assertTrue(MatrixE2eeSendPolicy.shouldUseE2ee(e2eeAvailable = true, roomEncrypted = true))
    }

    /**
     * **Validates: Requirements 8.5**
     *
     * The routing decision is a pure function - calling it multiple times with
     * different inputs always yields the correct result for that input,
     * demonstrating no stale state is cached between invocations.
     */
    @Test
    fun `Property 8 - decision is evaluated fresh on each invocation (not cached)`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 100), Arb.boolean(), Arb.boolean()) { e2eeAvailable, roomEncrypted ->
                // First call with one set of inputs
                val result1 = MatrixE2eeSendPolicy.shouldUseE2ee(e2eeAvailable, roomEncrypted)
                // Second call with inverted inputs
                val result2 = MatrixE2eeSendPolicy.shouldUseE2ee(!e2eeAvailable, !roomEncrypted)
                // Third call with original inputs should still give same result
                val result3 = MatrixE2eeSendPolicy.shouldUseE2ee(e2eeAvailable, roomEncrypted)

                val ctx = "e2eeAvailable=$e2eeAvailable, roomEncrypted=$roomEncrypted"
                assertEquals(e2eeAvailable && roomEncrypted, result1, "first call: $ctx")
                assertEquals(!e2eeAvailable && !roomEncrypted, result2, "inverted call: $ctx")
                assertEquals(result1, result3, "repeat call must match first call: $ctx")
            }
        }
    }
}
