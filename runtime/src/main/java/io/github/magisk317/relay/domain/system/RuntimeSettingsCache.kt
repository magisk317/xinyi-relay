package io.github.magisk317.relay.domain.system

import io.github.magisk317.relay.data.repository.SettingsRepository
import io.github.magisk317.relay.data.repository.SpecialAlertSettingsSnapshot

/**
 * Lightweight runtime settings cache to avoid frequent blocking reads.
 */
object RuntimeSettingsCache {
    private const val DEFAULT_TTL_MS = 2_000L

    @Volatile private var lastSpecialAlertFetchMs: Long = 0L
    @Volatile private var cachedSpecialAlert: SpecialAlertSettingsSnapshot? = null

    private data class BooleanEntry(
        val value: Boolean,
        val fetchedAt: Long,
    )

    private data class StringEntry(
        val value: String,
        val fetchedAt: Long,
    )

    private val booleanCache = mutableMapOf<String, BooleanEntry>()
    private val stringCache = mutableMapOf<String, StringEntry>()

    suspend fun getSpecialAlertSettings(
        settingsRepository: SettingsRepository,
        ttlMs: Long = DEFAULT_TTL_MS,
    ): SpecialAlertSettingsSnapshot {
        val now = System.currentTimeMillis()
        val cached = cachedSpecialAlert
        if (cached != null && now - lastSpecialAlertFetchMs <= ttlMs) {
            return cached
        }
        val fresh = settingsRepository.getSpecialAlertSettings()
        cachedSpecialAlert = fresh
        lastSpecialAlertFetchMs = now
        return fresh
    }

    suspend fun getBoolean(
        key: String,
        defaultValue: Boolean,
        ttlMs: Long = DEFAULT_TTL_MS,
        loader: suspend (String, Boolean) -> Boolean,
    ): Boolean {
        val now = System.currentTimeMillis()
        synchronized(booleanCache) {
            val cached = booleanCache[key]
            if (cached != null && now - cached.fetchedAt <= ttlMs) {
                return cached.value
            }
        }
        val fresh = loader(key, defaultValue)
        synchronized(booleanCache) {
            booleanCache[key] = BooleanEntry(fresh, now)
        }
        return fresh
    }

    suspend fun getString(
        key: String,
        defaultValue: String,
        ttlMs: Long = DEFAULT_TTL_MS,
        loader: suspend (String, String) -> String,
    ): String {
        val now = System.currentTimeMillis()
        synchronized(stringCache) {
            val cached = stringCache[key]
            if (cached != null && now - cached.fetchedAt <= ttlMs) {
                return cached.value
            }
        }
        val fresh = loader(key, defaultValue)
        synchronized(stringCache) {
            stringCache[key] = StringEntry(fresh, now)
        }
        return fresh
    }

    fun invalidate(key: String) {
        synchronized(booleanCache) {
            booleanCache.remove(key)
        }
        synchronized(stringCache) {
            stringCache.remove(key)
        }
    }

    fun clear() {
        cachedSpecialAlert = null
        lastSpecialAlertFetchMs = 0L
        synchronized(booleanCache) {
            booleanCache.clear()
        }
        synchronized(stringCache) {
            stringCache.clear()
        }
    }
}
