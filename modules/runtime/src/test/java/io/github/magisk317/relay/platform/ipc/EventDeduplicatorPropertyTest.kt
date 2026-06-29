package io.github.magisk317.relay.platform.ipc

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Feature: standard-mode-fallback, Property 6: Event Deduplication by EventId
 *
 * **Validates: Requirements 9.2, 9.3**
 *
 * For any event ID string, calling `EventDeduplicator.isDuplicate(eventId)` the first time
 * SHALL return `false`, and any subsequent call with the same `eventId` within the 10-second
 * window SHALL return `true`.
 */
class EventDeduplicatorPropertyTest : FunSpec({

    // Use unique prefixes per iteration to avoid cross-iteration state collisions
    // since EventDeduplicator is a singleton with mutable state.
    var iterationCounter = 0

    test("Property 6: first call returns false, second call within window returns true").config(
        invocations = 100,
    ) {
        checkAll(1, Arb.string(1..50).filter { it.isNotBlank() }) { baseEventId ->
            // Generate a unique eventId per iteration to avoid state collision across invocations
            val eventId = "prop6_${iterationCounter++}_$baseEventId"

            // First call: not yet seen → should return false
            val firstResult = EventDeduplicator.isDuplicate(eventId)
            firstResult shouldBe false

            // Second call: within 10s window → should return true
            val secondResult = EventDeduplicator.isDuplicate(eventId)
            secondResult shouldBe true
        }
    }

    test("Property 6: blank event IDs are never considered duplicates").config(
        invocations = 100,
    ) {
        // Blank strings should always return false (treated as non-duplicates)
        val blankResult1 = EventDeduplicator.isDuplicate("")
        blankResult1 shouldBe false

        val blankResult2 = EventDeduplicator.isDuplicate("   ")
        blankResult2 shouldBe false

        // Even calling twice, blanks should still return false
        val blankResult3 = EventDeduplicator.isDuplicate("")
        blankResult3 shouldBe false
    }
})
