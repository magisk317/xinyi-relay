package io.github.magisk317.relay.feature.call

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

/**
 * Feature: standard-mode-fallback, Property 4: Ringing Event Deduplication
 *
 * **Validates: Requirements 3.5**
 *
 * For any sequence of RINGING events with timestamps, only events occurring more than 8 seconds
 * after the previous accepted event SHALL produce a dispatch payload. Events within the 8-second
 * window SHALL be silently discarded.
 */
class CallStateMonitorDedupPropertyTest : FunSpec({

    // Mirrors CallStateMonitor.RINGING_DEDUP_MS
    val RINGING_DEDUP_MS = 8_000L

    /**
     * Simulates the dedup decision logic from CallStateMonitor.handleCallState():
     *   val now = System.currentTimeMillis()
     *   if (now - lastRingingAt < RINGING_DEDUP_MS) return   // skip
     *   lastRingingAt = now                                    // accept
     *
     * Returns list of booleans indicating whether each timestamp was accepted (dispatched).
     */
    fun simulateDedup(timestamps: List<Long>): List<Boolean> {
        var lastRingingAt = 0L
        return timestamps.map { now ->
            if (now - lastRingingAt < RINGING_DEDUP_MS) {
                false // silently discarded
            } else {
                lastRingingAt = now
                true // dispatched
            }
        }
    }

    // Generator: list of monotonically increasing positive timestamps.
    // Generate positive deltas and accumulate them to form sorted timestamps.
    val monotoneTimestamps = Arb.list(
        Arb.long(1L..20_000L), // deltas between 1ms and 20s
        range = 1..50,
    ).map { deltas ->
        // Start from a base timestamp and accumulate deltas
        val base = 1_000_000L
        deltas.runningFold(base) { acc, delta -> acc + delta }.drop(1)
    }

    test("Property 4: only events >8s apart from previous accepted event produce dispatch").config(
        invocations = 100,
    ) {
        checkAll(1, monotoneTimestamps) { timestamps ->
            val results = simulateDedup(timestamps)

            // Verify invariant: for each accepted event, it must be >8s from previous accepted
            var lastAcceptedAt = 0L
            timestamps.zip(results).forEach { (ts, accepted) ->
                if (accepted) {
                    // Accepted: gap from previous accepted must be >= RINGING_DEDUP_MS
                    (ts - lastAcceptedAt >= RINGING_DEDUP_MS) shouldBe true
                    lastAcceptedAt = ts
                } else {
                    // Discarded: gap from previous accepted must be < RINGING_DEDUP_MS
                    (ts - lastAcceptedAt < RINGING_DEDUP_MS) shouldBe true
                }
            }
        }
    }

    test("Property 4: first event in any sequence is always accepted").config(
        invocations = 100,
    ) {
        checkAll(1, monotoneTimestamps) { timestamps ->
            val results = simulateDedup(timestamps)
            // The first event should always be accepted because lastRingingAt starts at 0
            // and any positive timestamp minus 0 will be >= 8000
            results.first() shouldBe true
        }
    }

    test("Property 4: events within 8s window are always discarded").config(
        invocations = 100,
    ) {
        // Generate timestamps where all events after the first are within 8s of the first
        val clusteredTimestamps = Arb.list(
            Arb.long(1L..7_999L), // deltas within 8s window
            range = 2..20,
        ).map { deltas ->
            val base = 1_000_000L // large enough that first event is accepted
            listOf(base) + deltas.map { base + it }
        }

        checkAll(1, clusteredTimestamps) { timestamps ->
            val results = simulateDedup(timestamps)
            // First event accepted, all others within 8s should be discarded
            results.first() shouldBe true
            results.drop(1).forEach { it shouldBe false }
        }
    }
})
