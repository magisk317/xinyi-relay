package io.github.magisk317.relay.platform.ipc

import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Property 6: Event Deduplication by EventId
 *
 * **Validates: Requirements 9.2, 9.3**
 *
 * For any event ID string, calling `EventDeduplicator.isDuplicate(eventId)` the first time
 * SHALL return `false`, and any subsequent call with the same `eventId` within the 10-second
 * window SHALL return `true`.
 */
class EventDeduplicatorPropertyTest {

    // Use unique prefixes per iteration to avoid cross-iteration state collisions
    // since EventDeduplicator is a singleton with mutable state.
    private var iterationCounter = 0

    @Test
    fun `Property 6 - first call returns false, second call within window returns true`() {
        runBlocking {
            checkAll(100, Arb.string(1..50).filter { it.isNotBlank() }) { baseEventId ->
                // Generate a unique eventId per iteration to avoid state collision across invocations
                val eventId = "prop6_${iterationCounter++}_$baseEventId"

                // First call: not yet seen → should return false
                val firstResult = EventDeduplicator.isDuplicate(eventId)
                assertFalse(firstResult, "first isDuplicate($eventId) should be false")

                // Second call: within 10s window → should return true
                val secondResult = EventDeduplicator.isDuplicate(eventId)
                assertTrue(secondResult, "second isDuplicate($eventId) within window should be true")
            }
        }
    }

    // NOTE: the original kotest test used `.config(invocations = 100)` on a deterministic body with
    // no generated input; a single run is equivalent.
    @Test
    fun `Property 6 - blank event IDs are never considered duplicates`() {
        // Blank strings should always return false (treated as non-duplicates)
        val blankResult1 = EventDeduplicator.isDuplicate("")
        assertFalse(blankResult1, "empty event ID should not be a duplicate")

        val blankResult2 = EventDeduplicator.isDuplicate("   ")
        assertFalse(blankResult2, "whitespace event ID should not be a duplicate")

        // Even calling twice, blanks should still return false
        val blankResult3 = EventDeduplicator.isDuplicate("")
        assertFalse(blankResult3, "repeated empty event ID should not be a duplicate")
    }
}
