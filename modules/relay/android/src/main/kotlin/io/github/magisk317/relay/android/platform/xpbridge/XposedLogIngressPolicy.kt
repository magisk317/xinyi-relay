package io.github.magisk317.relay.android.platform.xpbridge

import android.content.Context

internal data class UidQuotaSnapshot(
    val utcDay: Long,
    val events: Int,
    val bytes: Long,
)

internal interface UidQuotaStore {
    fun read(uid: Int): UidQuotaSnapshot?

    fun write(uid: Int, snapshot: UidQuotaSnapshot): Boolean
}

internal class PersistentUidQuota(
    private val store: UidQuotaStore,
    private val maxEventsPerDay: Int,
    private val maxBytesPerDay: Long,
    private val wallClockMillis: () -> Long,
) {
    init {
        require(maxEventsPerDay > 0) { "maxEventsPerDay must be positive" }
        require(maxBytesPerDay > 0) { "maxBytesPerDay must be positive" }
    }

    @Synchronized
    fun tryConsume(uid: Int, bytes: Long): Boolean {
        val now = wallClockMillis()
        if (uid < 0 || bytes < 0 || bytes > maxBytesPerDay || now < 0) return false

        val currentDay = now / MILLIS_PER_DAY
        val stored = try {
            store.read(uid)
        } catch (_: Exception) {
            return false
        }
        val active = when {
            stored == null -> UidQuotaSnapshot(currentDay, events = 0, bytes = 0)
            currentDay > stored.utcDay -> UidQuotaSnapshot(currentDay, events = 0, bytes = 0)
            else -> stored
        }
        if (active.events >= maxEventsPerDay) return false
        if (active.bytes > maxBytesPerDay - bytes) return false

        val updated = active.copy(events = active.events + 1, bytes = active.bytes + bytes)
        return try {
            store.write(uid, updated)
        } catch (_: Exception) {
            false
        }
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}

internal class SharedPreferencesUidQuotaStore(context: Context) : UidQuotaStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(uid: Int): UidQuotaSnapshot? {
        val dayKey = key(uid, "day")
        if (!preferences.contains(dayKey)) return null
        return UidQuotaSnapshot(
            utcDay = preferences.getLong(dayKey, 0L),
            events = preferences.getInt(key(uid, "events"), 0).coerceAtLeast(0),
            bytes = preferences.getLong(key(uid, "bytes"), 0L).coerceAtLeast(0L),
        )
    }

    override fun write(uid: Int, snapshot: UidQuotaSnapshot): Boolean =
        preferences.edit()
            .putLong(key(uid, "day"), snapshot.utcDay)
            .putInt(key(uid, "events"), snapshot.events)
            .putLong(key(uid, "bytes"), snapshot.bytes)
            .commit()

    private fun key(uid: Int, suffix: String): String = "uid_${uid}_$suffix"

    private companion object {
        const val PREFS_NAME = "xposed_log_ingress_quota"
    }
}
