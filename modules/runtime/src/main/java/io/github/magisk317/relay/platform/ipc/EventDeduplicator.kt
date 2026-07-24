package io.github.magisk317.relay.platform.ipc

import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * Singleton that prevents duplicate event processing when both Enhanced Mode
 * (Xposed hooks) and Standard Mode (system APIs) produce payloads for the
 * same event.
 *
 * Uses a time-window based approach (10s) with a 200-entry cap.
 * The underlying LinkedHashMap with access-order provides LRU eviction behavior.
 */
object EventDeduplicator {
    private const val WINDOW_MS = 10_000L
    private const val MAX_ENTRIES = 200

    private val recentEvents = LinkedHashMap<String, Long>(64, 0.75f, true)

    /**
     * Returns true if [eventId] was already seen within the dedup window.
     * If not seen, records it and returns false.
     *
     * Blank event IDs are treated as non-duplicates (returns false).
     */
    @Synchronized
    fun isDuplicate(eventId: String): Boolean {
        if (eventId.isBlank()) {
            MagiskOtel.event(
                name = "sms.event",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "app",
                    "stage" to "dedupe",
                    "reason" to "blank_event_id",
                    "event_id_present" to "false",
                ),
                statusOk = true,
            )
            return false
        }
        val now = System.currentTimeMillis()
        evict(now)
        val existing = recentEvents[eventId]
        if (existing != null) {
            MagiskOtel.event(
                name = "sms.event",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to (now - existing).coerceAtLeast(0L).toString(),
                    "process" to "app",
                    "stage" to "dedupe",
                    "reason" to "duplicate_window",
                    "event_id_present" to "true",
                ),
                statusOk = true,
            )
            return true
        }
        recentEvents[eventId] = now
        MagiskOtel.event(
            name = "sms.event",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "dedupe",
                "reason" to "unique",
                "event_id_present" to "true",
            ),
            statusOk = true,
        )
        return false
    }

    private fun evict(now: Long) {
        val iterator = recentEvents.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > WINDOW_MS) iterator.remove()
            else break
        }
        while (recentEvents.size > MAX_ENTRIES) {
            recentEvents.remove(recentEvents.keys.first())
        }
    }
}
