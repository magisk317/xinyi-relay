package io.github.magisk317.relay.desktop.core

import io.github.magisk317.relay.desktop.core.store.Clock
import io.github.magisk317.relay.desktop.core.store.DesktopLocalStore
import io.github.magisk317.relay.desktop.data.DesktopDatabase
import io.github.magisk317.relay.desktop.data.DesktopDatabaseFactory
import io.github.magisk317.relay.desktop.data.entity.DeviceEntity

/**
 * Deterministic clock: every call returns a distinct, monotonically increasing
 * timestamp so ordering assertions on `created_at` are stable.
 */
class FixedClock(private val startMillis: Long = 1_700_000_000_000L) : Clock {

    private var cursor = startMillis

    override fun nowMillis(): Long = cursor++

    override fun nowRfc3339(): String = toRfc3339(nowMillis())

    override fun toRfc3339(millis: Long): String {
        // Fixed-width 3-digit millis so lexicographic byte order == chronological
        // order. `Instant.toString()` is NOT lexicographically stable: it drops the
        // ".SSS" for whole seconds and then appends "Z", so a naive
        // `ORDER BY created_at` would misorder. Normalise both to `…ss.SSSZ`.
        val instant = java.time.Instant.ofEpochMilli(millis)
        val dateTime = instant.toString().removeSuffix("Z").substringBefore('.')
        return String.format("%s.%03dZ", dateTime, instant.nano / 1_000_000)
    }
}

/**
 * Opens an in-memory store for tests.
 */
internal fun testStore(clock: Clock = FixedClock()): Pair<DesktopDatabase, DesktopLocalStore> {
    val database = DesktopDatabaseFactory.inMemory()
    return database to DesktopLocalStore(database, clock)
}

internal suspend fun DesktopDatabase.seedDevice(id: Long, name: String = "device-$id") {
    deviceDao().insert(
        DeviceEntity(
            id = id,
            userId = 1L,
            deviceName = name,
            displayName = name,
            createdAt = "2026-10-02T00:00:00Z",
            updatedAt = "2026-10-02T00:00:00Z",
        ),
    )
}
