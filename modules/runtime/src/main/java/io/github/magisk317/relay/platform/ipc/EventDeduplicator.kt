package io.github.magisk317.relay.platform.ipc

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
        if (eventId.isBlank()) return false
        val now = System.currentTimeMillis()
        evict(now)
        val existing = recentEvents[eventId]
        if (existing != null) return true
        recentEvents[eventId] = now
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
