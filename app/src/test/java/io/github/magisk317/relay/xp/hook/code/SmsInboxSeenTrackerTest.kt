package io.github.magisk317.relay.xp.hook.code

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsInboxSeenTrackerTest {

    @Test
    fun markSeen_rejectsDuplicates() {
        val tracker = SmsInboxSeenTracker(maxTrackedSmsIds = 4)

        assertTrue(tracker.markSeen(1001L))
        assertFalse(tracker.markSeen(1001L))
    }

    @Test
    fun markSeen_evictsOldestWhenCapacityExceeded() {
        val tracker = SmsInboxSeenTracker(maxTrackedSmsIds = 2)

        assertTrue(tracker.markSeen(1L))
        assertTrue(tracker.markSeen(2L))
        assertTrue(tracker.markSeen(3L))
        assertTrue(tracker.markSeen(1L))
    }
}
