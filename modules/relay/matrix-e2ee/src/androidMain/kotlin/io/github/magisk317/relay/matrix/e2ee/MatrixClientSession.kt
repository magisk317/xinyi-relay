package io.github.magisk317.relay.matrix.e2ee
import io.github.magisk317.forward.contract.model.ProxyType
import android.content.Context
import io.github.magisk317.forward.sender.SenderSettingSanitizer
import io.github.magisk317.forward.sender.config.MatrixSetting
import java.io.File
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientBuilder
import org.matrix.rustcomponents.sdk.Session
import org.matrix.rustcomponents.sdk.SyncSettingsV2
import org.matrix.rustcomponents.sdk.Timeline

/**
 * Client lifecycle and session persistence for the Matrix E2EE runtime.
 * Owns the cached client/session state; MatrixE2eeRuntime keeps the send
 * pipeline, diagnostics and auth classification.
 */
@Suppress("TooGenericExceptionCaught", "DEPRECATION")
internal object MatrixClientSession {
    private const val TAG = "MatrixE2eeUtils"

    private const val WHOAMI_CACHE_FILENAME = "whoami_cache.json"
    private const val LOGIN_SESSION_FILENAME = "session.json"
    private const val LOGIN_DEVICE_DISPLAY_NAME = "XinyiRelay"
    private const val CLIENT_INIT_TIMEOUT_MS = 30_000L // 30 seconds
    private const val E2EE_INIT_TIMEOUT_MS = 15_000L
    private const val INITIAL_KEY_SYNC_TIMEOUT_MS = 30_000L

    private val clientMutex = Mutex()
    private var cachedClient: Client? = null
    private var cachedAccessToken: String? = null
    // Tracks whether the initial sync has been performed for the current client.
    // The SDK's high-level Timeline API handles encryption automatically,
    // but requires at least one sync to:
    // 1. Upload device identity keys and one-time keys to the homeserver
    // 2. Process incoming to-device events (key shares, Olm session establishment)
    // 3. Replenish one-time keys when the server-side count falls below threshold
    // After the initial sync, these operations are triggered internally by the SDK
    // on subsequent sends.
    private var initialSyncDone: Boolean = false

    /**
     * Holds the result of a /whoami API call.
     */
    internal data class WhoamiInfo(
        val userId: String,
        val deviceId: String
    )

    internal suspend fun ensureKeySyncReady(client: Client) {
        if (initialSyncDone) return

        // Wait for background E2EE initialization tasks (key upload, etc.)
        // that were started by restoreSession(). This ensures device keys are
        // uploaded to the homeserver before we attempt to send.
        SLog.d(TAG, "Waiting for E2EE initialization tasks to complete...")
        withTimeout(E2EE_INIT_TIMEOUT_MS) {
            client.encryption().waitForE2eeInitializationTasks()
        }
        SLog.d(TAG, "E2EE initialization tasks completed")

        // Attempt a quick sync to process to-device events and establish
        // Megolm sessions. Use a short timeout — if the server takes too long
        // (e.g. large initial sync), skip it and let Timeline send handle key
        // distribution inline (the SDK does this automatically).
        try {
            SLog.d(TAG, "Performing initial sync for key synchronization...")
            withTimeout(INITIAL_KEY_SYNC_TIMEOUT_MS) {
                client.syncOnceV2(SyncSettingsV2(timeoutMs = 0u.toULong()))
            }
            SLog.i(TAG, "Initial key sync completed successfully")
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException(
                "Initial key sync timed out before E2EE send: ${e.message}",
                e,
            )
        } catch (e: Exception) {
            throw IllegalStateException(
                "Initial key sync failed before E2EE send: " +
                    "${e.javaClass.simpleName}: ${e.message}",
                e,
            )
        }

        initialSyncDone = true
    }

    internal suspend fun invalidateClientAndSession(context: Context, setting: MatrixSetting) {
        val safeSetting = SenderSettingSanitizer.sanitizeMatrixSetting(setting)
        val useLoginMode = safeSetting.username.isNotBlank() && safeSetting.password.isNotBlank()
        clientMutex.withLock {
            cachedClient?.let { client ->
                closeClientSafely(client)
                cachedClient = null
            }
            cachedAccessToken = null
            initialSyncDone = false
            val cacheKey = if (useLoginMode) safeSetting.username.trim() else safeSetting.accessToken.trim()
            if (cacheKey.isNotBlank()) {
                val storeDir = getStoreDir(context, cacheKey)
                val sessionFile = File(storeDir, LOGIN_SESSION_FILENAME)
                if (sessionFile.exists()) {
                    sessionFile.delete()
                    SLog.w(TAG, "Deleted stale session file after 401 auth error: ${sessionFile.absolutePath}")
                }
            }
        }
    }

    internal suspend fun getOrCreateClient(
        context: Context,
        setting: MatrixSetting,
        forceRefresh: Boolean = false,
    ): Client {
        // In login mode, cache key is username; in token mode, it's accessToken
        val useLoginMode = setting.username.isNotBlank() && setting.password.isNotBlank()
        val cacheKey = if (useLoginMode) setting.username.trim() else setting.accessToken.trim()
        val token = setting.accessToken.trim()
        clientMutex.withLock {
            if (forceRefresh) {
                cachedClient?.let { closeClientSafely(it) }
                cachedClient = null
                cachedAccessToken = null
                initialSyncDone = false
                if (useLoginMode) {
                    val storeDir = getStoreDir(context, cacheKey)
                    val sessionFile = File(storeDir, LOGIN_SESSION_FILENAME)
                    if (sessionFile.exists()) {
                        sessionFile.delete()
                    }
                }
            }

            // Return cached client if credentials haven't changed
            cachedClient?.let { client ->
                if (cachedAccessToken == cacheKey) return client
                // Access token changed — close old client safely before building new one
                closeClientSafely(client)
                cachedClient = null
                cachedAccessToken = null
                initialSyncDone = false
            }

            // Build new client with a 30-second timeout
            try {
                val client = withTimeout(CLIENT_INIT_TIMEOUT_MS) {
                    buildAndRestoreClient(context, setting, token)
                }
                cachedAccessToken = cacheKey
                return client
            } catch (e: TimeoutCancellationException) {
                SLog.e(TAG, "Client initialization timed out after ${CLIENT_INIT_TIMEOUT_MS}ms")
                throw IllegalStateException(
                    "CryptoEngine initialization timed out (${CLIENT_INIT_TIMEOUT_MS / 1000}s limit exceeded)",
                    e
                )
            }
        }
    }

    internal suspend fun getClientForVerification(
        context: Context,
        setting: MatrixSetting,
        forceRefresh: Boolean = false,
    ): Client {
        val safeSetting = SenderSettingSanitizer.sanitizeMatrixSetting(setting)
        return getOrCreateClient(context, safeSetting, forceRefresh)
    }

    /**
     * Build a new Client instance and restore session.
     * This is separated from getOrCreateClient to keep the timeout boundary clear.
     *
     * If build() or restoreSession() throws a store-related exception (e.g. database corruption,
     * crypto store errors), the store directory is deleted and the operation is retried once.
     * If the retry also fails, the exception propagates (triggering plaintext fallback).
     */
    internal suspend fun buildAndRestoreClient(context: Context, setting: MatrixSetting, token: String): Client {
        // Determine store directory based on auth mode
        val useLoginMode = setting.username.isNotBlank() && setting.password.isNotBlank()
        val storeDir: File
        val whoami: WhoamiInfo?

        if (useLoginMode) {
            // Login mode: use username hash for store directory (stable across token changes)
            storeDir = getStoreDir(context, setting.username.trim())
            whoami = null
        } else {
            // Legacy token mode: resolve userId via whoami
            whoami = resolveWhoami(context, setting)
            storeDir = getStoreDir(context, whoami.userId)
            saveWhoamiCache(storeDir, whoami, token)
        }

        return try {
            buildClientWithSession(setting, storeDir, whoami, token)
        } catch (e: Exception) {
            val messageLower = (e.message ?: "").lowercase()
            val isAuthError = messageLower.contains("m_unknown_token") ||
                              messageLower.contains("token is not active") ||
                              messageLower.contains("unauthorized") ||
                              messageLower.contains("authentication")

            if (isStoreCorruptionException(e) || isAuthError) {
                SLog.w(
                    TAG,
                    "Crypto store corrupted or auth failed, deleting store and retrying. " +
                        "Reason: ${e.javaClass.simpleName}: ${e.message}",
                    e,
                )
                // Delete the corrupted store directory and session file
                storeDir.deleteRecursively()
                // Recreate the store directory
                storeDir.mkdirs()
                // Re-save whoami cache after directory recreation (only in token mode)
                if (whoami != null) {
                    saveWhoamiCache(storeDir, whoami, token)
                }
                // Retry once — if this also fails, let it propagate
                buildClientWithSession(setting, storeDir, whoami, token)
            } else {
                throw e
            }
        }.also { client ->
            cachedClient = client
            // Enable send queues so the SDK processes outgoing to-device messages
            // (Megolm session key distribution) during sync.
            client.enableAllSendQueues(true)
        }
    }

    /**
     * Builds a Client and restores the session. Throws on any failure.
     * Extracted to allow retry logic in [buildAndRestoreClient].
     */
    internal suspend fun buildClientWithSession(
        setting: MatrixSetting,
        storeDir: File,
        whoami: WhoamiInfo?,
        token: String,
    ): Client {
        val useLoginMode = setting.username.isNotBlank() && setting.password.isNotBlank()
        if (useLoginMode) {
            prepareLoginStoreForExpectedSession(setting, storeDir)
        }

        SLog.d(TAG, "Matrix E2EE room key recipient strategy: all_devices")
        val builder = ClientBuilder()
            .homeserverUrl(MatrixE2eeSendPolicy.normalizeHomeserverForSession(setting.homeserver))
            .sessionPaths(storeDir.absolutePath, storeDir.absolutePath)
            .autoEnableCrossSigning(true)
            // Distribute Megolm session keys to ALL devices in the room,
            // regardless of cross-signing verification status. Without this,
            // the default IDENTITY_BASED_STRATEGY only shares keys with devices
            // signed by their owner's cross-signing identity — causing
            // "sender's device has not sent us the keys" for unverified recipients.
            .roomKeyRecipientStrategy(uniffi.matrix_sdk_crypto.CollectStrategy.ALL_DEVICES)

        // Configure proxy if needed
        if (setting.proxyType != ProxyType.DIRECT &&
            setting.proxyHost.isNotBlank() &&
            setting.proxyPort.isNotBlank()
        ) {
            val port = setting.proxyPort.toIntOrNull() ?: 0
            if (port > 0) {
                val proxyUrl = when (setting.proxyType) {
                    ProxyType.HTTP -> "http://${setting.proxyHost}:$port"
                    ProxyType.SOCKS -> "socks5://${setting.proxyHost}:$port"
                    else -> null
                }
                proxyUrl?.let { builder.proxy(it) }
            }
        }

        val client = builder.build()

        // Authenticate: prefer username+password login (stable device session),
        // fall back to restoreSession with access token.
        if (useLoginMode) {
            val sessionFile = File(storeDir, LOGIN_SESSION_FILENAME)
            if (sessionFile.exists()) {
                try {
                    val sessionStr = sessionFile.readText()
                    val sessionJson = JSONObject(sessionStr)
                    val session = Session(
                        accessToken = sessionJson.getString("accessToken"),
                        refreshToken = sessionJson.optString("refreshToken").takeIf { it.isNotBlank() },
                        userId = sessionJson.getString("userId"),
                        deviceId = sessionJson.getString("deviceId"),
                        homeserverUrl = sessionJson.getString("homeserverUrl"),
                        oauthData = null,
                        slidingSyncVersion = org.matrix.rustcomponents.sdk.SlidingSyncVersion.NONE,
                    )
                    client.restoreSession(session)
                    SLog.d(TAG, "Restored session for ${session.userId} (${session.deviceId})")
                    return client
                } catch (e: Exception) {
                    SLog.w(TAG, "Failed to restore session from file: ${e.message}, will login fresh")
                }
            }

            // Login with credentials — SDK manages token lifecycle, device_id is stable.
            try {
                SLog.d(TAG, "Logging in with username/password...")
                client.login(
                    setting.username.trim(),
                    setting.password,
                    LOGIN_DEVICE_DISPLAY_NAME,
                    MatrixE2eeSendPolicy.LOGIN_DEVICE_ID,
                )
                SLog.i(TAG, "Login successful")

                // Save session for future restore
                val session = client.session()
                val sessionJson = JSONObject().apply {
                    put("accessToken", session.accessToken)
                    put("refreshToken", session.refreshToken ?: "")
                    put("userId", session.userId)
                    put("deviceId", session.deviceId)
                    put("homeserverUrl", session.homeserverUrl)
                }
                sessionFile.writeText(sessionJson.toString())
            } catch (e: Exception) {
                closeClientSafely(client)
                throw IllegalStateException("Matrix login failed: ${e.message}", e)
            }
        } else {
            // Legacy: restore session with manually provided access token
            try {
                client.restoreSession(
                    Session(
                        accessToken = token,
                        refreshToken = null,
                        userId = whoami?.userId ?: "",
                        deviceId = whoami?.deviceId ?: "",
                        homeserverUrl = MatrixE2eeSendPolicy.normalizeHomeserverForSession(setting.homeserver),
                        oauthData = null,
                        slidingSyncVersion = org.matrix.rustcomponents.sdk.SlidingSyncVersion.NONE,
                    )
                )
            } catch (e: Exception) {
                closeClientSafely(client)
                throw e
            }
        }

        return client
    }

    /**
     * Login mode intentionally pins the Matrix device id so users can verify one stable relay device.
     * Older cached stores may contain a session restored from an arbitrary token-created device; those
     * stores must be cleared before building the SDK client because the crypto store is device-scoped.
     */
    internal fun prepareLoginStoreForExpectedSession(setting: MatrixSetting, storeDir: File) {
        val sessionFile = File(storeDir, LOGIN_SESSION_FILENAME)
        if (!sessionFile.exists()) {
            if (storeDir.listFiles()?.isNotEmpty() == true) {
                clearLoginStore(storeDir, "missing cached login session")
            }
            return
        }

        val sessionJson = try {
            JSONObject(sessionFile.readText())
        } catch (e: Exception) {
            SLog.w(TAG, "Failed to read cached Matrix login session metadata: ${e.message}")
            clearLoginStore(storeDir, "unreadable cached login session")
            return
        }

        val cachedDeviceId = sessionJson.optString("deviceId", "")
        val cachedHomeserver = sessionJson.optString("homeserverUrl", "")
        val expectedHomeserver = MatrixE2eeSendPolicy.normalizeHomeserverForSession(setting.homeserver)
        if (!MatrixE2eeSendPolicy.shouldReuseLoginSession(cachedDeviceId, cachedHomeserver, expectedHomeserver)) {
            val reason = when {
                cachedDeviceId != MatrixE2eeSendPolicy.LOGIN_DEVICE_ID -> "device_id_mismatch"
                MatrixE2eeSendPolicy.normalizeHomeserverForSession(cachedHomeserver) != expectedHomeserver ->
                    "homeserver_mismatch"
                else -> "unknown"
            }
            SLog.w(TAG, "Cached Matrix login session does not match expected relay device; clearing store. reason=$reason")
            clearLoginStore(storeDir, reason)
        }
    }

    internal fun clearLoginStore(storeDir: File, reason: String) {
        SLog.w(TAG, "Clearing Matrix login crypto store for fresh login: reason=$reason")
        storeDir.deleteRecursively()
        storeDir.mkdirs()
    }

    /**
     * Determines whether an exception indicates crypto store corruption.
     * Common indicators: database corruption, SQLite errors, crypto store deserialization failures,
     * schema version mismatches, or file I/O errors in the store layer.
     */
    internal fun isStoreCorruptionException(e: Exception): Boolean {
        val message = (e.message ?: "") + (e.cause?.message ?: "")
        val messageLower = message.lowercase()
        return messageLower.contains("corrupt") ||
            messageLower.contains("database") ||
            messageLower.contains("sqlite") ||
            messageLower.contains("crypto store") ||
            messageLower.contains("cryptostore") ||
            messageLower.contains("deseriali") ||
            messageLower.contains("schema") ||
            messageLower.contains("migration") ||
            messageLower.contains("open_store") ||
            messageLower.contains("openstore") ||
            messageLower.contains("sled") ||
            messageLower.contains("io error")
    }

    /**
     * Safely close a Client instance, catching any exceptions to prevent
     * cleanup from disrupting the main flow.
     */
    internal fun closeClientSafely(client: Client) {
        try {
            client.close()
            SLog.d(TAG, "Previous client instance closed successfully")
        } catch (e: Exception) {
            SLog.w(TAG, "Error closing client instance (non-fatal): ${e.message}")
        }
    }

    /**
     * Resolve the userId and deviceId for the current session.
     *
     * Resolution order:
     * 1. Try to load from cached whoami file in the store directory (keyed by accessToken hash)
     * 2. Call /_matrix/client/v3/account/whoami API
     * 3. If whoami fails (network error, token expired), throw an exception
     *    since userId/deviceId are required for correct Session construction.
     */
    internal fun resolveWhoami(context: Context, setting: MatrixSetting): WhoamiInfo {
        val token = setting.accessToken.trim()

        // Try loading from cache first (keyed by token hash to find the right store dir)
        val cachedResult = loadWhoamiCacheByToken(context, token)
        if (cachedResult != null) {
            SLog.d(TAG, "Using cached whoami result for userId=${cachedResult.userId}")
            return cachedResult
        }

        // Call /whoami API
        val homeserver = setting.homeserver.trim().trimEnd('/')
        val url = "$homeserver/_matrix/client/v3/account/whoami"
        return try {
            val response = MatrixUtils.executeGet(setting, url, token)
            if (response.isSuccessful) {
                val json = JSONObject(response.body)
                val userId = json.getString("user_id")
                val deviceId = json.optString("device_id", "")
                if (userId.isBlank()) {
                    throw IllegalStateException("whoami returned blank user_id")
                }
                SLog.d(TAG, "whoami resolved: userId=$userId, deviceId=$deviceId")
                WhoamiInfo(userId = userId, deviceId = deviceId)
            } else {
                val code = response.code
                SLog.e(TAG, "whoami failed with HTTP $code")
                throw IllegalStateException(
                    "whoami API failed with HTTP $code. " +
                        "Cannot construct Session without userId/deviceId."
                )
            }
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            SLog.e(TAG, "whoami request failed: ${e.message}")
            throw IllegalStateException(
                "whoami request failed: ${e.message}. " +
                    "Cannot construct Session without userId/deviceId.",
                e
            )
        }
    }

    /**
     * Try to load cached whoami info by scanning existing store directories for a matching token.
     * The cache file stores the accessToken hash so we can validate it matches.
     */
    internal fun loadWhoamiCacheByToken(context: Context, accessToken: String): WhoamiInfo? {
        val tokenHash = MatrixE2eeSendPolicy.sha256Hex(accessToken).take(16)
        val cryptoDir = File(context.filesDir, "matrix-crypto")
        if (!cryptoDir.exists()) return null

        // Scan existing store directories for a matching cache file
        val storeDirs = cryptoDir.listFiles() ?: return null
        for (dir in storeDirs) {
            if (!dir.isDirectory) continue
            val cacheFile = File(dir, WHOAMI_CACHE_FILENAME)
            if (!cacheFile.exists()) continue
            try {
                val json = JSONObject(cacheFile.readText())
                val cachedTokenHash = json.optString("token_hash", "")
                if (cachedTokenHash == tokenHash) {
                    val userId = json.getString("user_id")
                    val deviceId = json.optString("device_id", "")
                    if (userId.isNotBlank()) {
                        return WhoamiInfo(userId = userId, deviceId = deviceId)
                    }
                }
            } catch (e: Exception) {
                SLog.w(TAG, "Failed to read whoami cache from ${dir.name}: ${e.message}")
                // Continue scanning other directories
            }
        }
        return null
    }

    /**
     * Save whoami result to the store directory for future use.
     * Includes a token hash so we can validate the cache matches the current token.
     */
    internal fun saveWhoamiCache(storeDir: File, whoami: WhoamiInfo, accessToken: String) {
        val tokenHash = MatrixE2eeSendPolicy.sha256Hex(accessToken).take(16)
        val cacheFile = File(storeDir, WHOAMI_CACHE_FILENAME)
        try {
            val json = JSONObject().apply {
                put("user_id", whoami.userId)
                put("device_id", whoami.deviceId)
                put("token_hash", tokenHash)
            }
            cacheFile.writeText(json.toString())
        } catch (e: Exception) {
            SLog.w(TAG, "Failed to write whoami cache: ${e.message}")
            // Non-fatal: next time we'll just call /whoami again
        }
    }

    /**
     * Compute user-isolated store directory path.
     * Format: {appFilesDir}/matrix-crypto/{sha256(userId).take(16)}/
     */
    internal fun getStoreDir(context: Context, userId: String): File {
        val hash = MatrixE2eeSendPolicy.sha256Hex(userId).take(16)
        val dir = File(context.filesDir, "matrix-crypto/$hash")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    internal suspend fun forceClearDeviceStore(context: Context, setting: MatrixSetting) {
        val safeSetting = SenderSettingSanitizer.sanitizeMatrixSetting(setting)
        val useLoginMode = safeSetting.username.isNotBlank() && safeSetting.password.isNotBlank()

        clientMutex.withLock {
            cachedClient?.let { client ->
                SLog.w(TAG, "Force clearing device: closing existing client")
                closeClientSafely(client)
                cachedClient = null
            }
            cachedAccessToken = null
            initialSyncDone = false

            val cacheKey = if (useLoginMode) safeSetting.username.trim() else safeSetting.accessToken.trim()
            if (cacheKey.isNotBlank()) {
                val storeDir = getStoreDir(context, cacheKey)
                SLog.w(TAG, "Force clearing device: deleting store directory $storeDir")
                storeDir.deleteRecursively()
                storeDir.mkdirs()
            }
        }
    }
}
