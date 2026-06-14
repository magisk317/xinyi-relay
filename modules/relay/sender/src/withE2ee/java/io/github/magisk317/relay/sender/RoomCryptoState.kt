package io.github.magisk317.relay.sender

import io.github.magisk317.relay.net.RelayHttpClients
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Queries and caches the encryption state of Matrix rooms.
 *
 * A room is considered encrypted if and only if its `m.room.encryption` state event
 * returns HTTP 200 with `algorithm == "m.megolm.v1.aes-sha2"`. All other conditions
 * (404, network errors, parse failures, unsupported algorithms) are treated as unencrypted.
 *
 * Cache entries expire after [CACHE_TTL_MS] (60 minutes). The cache is cleared on process restart.
 */
internal object RoomCryptoState {

    private const val TAG = "RoomCryptoState"
    private const val SUPPORTED_ALGORITHM = "m.megolm.v1.aes-sha2"
    internal const val CACHE_TTL_MS = 60 * 60 * 1000L // 60 minutes
    private const val QUERY_TIMEOUT_MS = 10_000L
    private const val HTTP_OK = 200
    private const val HTTP_UNAUTHORIZED = 401
    private const val HTTP_FORBIDDEN = 403

    data class EncryptionInfo(
        val encrypted: Boolean,
        val algorithm: String?,
        val queriedAt: Long // System.currentTimeMillis()
    )

    internal val cache = ConcurrentHashMap<String, EncryptionInfo>()

    private val lenientJson = Json { ignoreUnknownKeys = true }

    /**
     * Check whether [roomId] has E2EE encryption enabled.
     *
     * Returns `true` only when the room state query succeeds (HTTP 200) and the
     * `algorithm` field equals [SUPPORTED_ALGORITHM]. All failures are treated as
     * unencrypted to allow plaintext fallback.
     *
     * Results are cached per room for up to 60 minutes.
     */
    suspend fun isRoomEncrypted(
        homeserver: String,
        accessToken: String,
        roomId: String,
        client: OkHttpClient = buildQueryClient()
    ): Boolean {
        // Check cache first
        val cached = cache[roomId]
        if (cached != null && !isExpired(cached)) {
            return cached.encrypted
        }

        // Query the room encryption state
        val info = queryRoomEncryption(homeserver, accessToken, roomId, client)
        cache[roomId] = info
        return info.encrypted
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun queryRoomEncryption(
        homeserver: String,
        accessToken: String,
        roomId: String,
        client: OkHttpClient
    ): EncryptionInfo = withContext(Dispatchers.IO) {
        try {
            val url = buildStateUrl(homeserver, roomId)
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer ${accessToken.trim()}")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body.string()

                if (response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN) {
                    // Auth failure — don't cache this result and don't treat as "unencrypted".
                    // Throw so the caller knows the token is invalid.
                    SLog.w(TAG, "Room state query returned HTTP ${response.code} for room=$roomId (auth error, not caching)")
                    throw IllegalStateException("Room encryption query failed: HTTP ${response.code} (token may be expired)")
                }

                if (response.code != HTTP_OK) {
                    SLog.d(TAG, "Room state query returned HTTP ${response.code} for room=$roomId")
                    return@withContext EncryptionInfo(
                        encrypted = false,
                        algorithm = null,
                        queriedAt = System.currentTimeMillis()
                    )
                }

                val algorithm = parseAlgorithm(bodyString)

                if (algorithm == SUPPORTED_ALGORITHM) {
                    SLog.d(TAG, "Room $roomId is encrypted (algorithm=$algorithm)")
                    EncryptionInfo(
                        encrypted = true,
                        algorithm = algorithm,
                        queriedAt = System.currentTimeMillis()
                    )
                } else {
                    if (algorithm != null) {
                        SLog.w(TAG, "Room $roomId has unsupported encryption algorithm: $algorithm")
                    } else {
                        SLog.d(TAG, "Room $roomId: encryption state missing algorithm field")
                    }
                    EncryptionInfo(
                        encrypted = false,
                        algorithm = algorithm,
                        queriedAt = System.currentTimeMillis()
                    )
                }
            }
        } catch (e: Exception) {
            SLog.w(TAG, "Room encryption query failed for room=$roomId: ${e.message}", e)
            EncryptionInfo(
                encrypted = false,
                algorithm = null,
                queriedAt = System.currentTimeMillis()
            )
        }
    }

    private fun parseAlgorithm(body: String): String? {
        return try {
            val json = lenientJson.decodeFromString<JsonObject>(body)
            json["algorithm"]?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }

    internal fun isExpired(info: EncryptionInfo): Boolean {
        return System.currentTimeMillis() - info.queriedAt >= CACHE_TTL_MS
    }

    internal fun buildStateUrl(homeserver: String, roomId: String): String {
        val base = homeserver.trim().trimEnd('/')
        val normalizedRoomId = roomId.trim()
        return "$base/_matrix/client/v3/rooms/$normalizedRoomId/state/m.room.encryption"
    }

    private fun buildQueryClient(): OkHttpClient {
        return RelayHttpClients.newBuilder()
            .connectTimeout(QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    /** Clears the in-memory cache. Primarily for testing. */
    internal fun clearCache() {
        cache.clear()
    }
}
