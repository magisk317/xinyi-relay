package io.github.magisk317.relay.sender

import io.github.magisk317.relay.net.RelayHttpClients
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Request
import java.security.MessageDigest
import java.util.UUID

/**
 * Microsoft 365 OAuth2 service for email senders.
 *
 * Implements the device-code grant (public-client flow): no client secret is stored
 * on the device. Credentials live in an encrypted [EmailOAuthCredentialStore] and
 * are never written into the sender JSON, remote mirror, or backup.
 */
class OAuth2Service(
    private val credentialStore: EmailOAuthCredentialStore,
    private val httpClient: okhttp3.OkHttpClient = RelayHttpClients.default,
) : EmailOAuthService {
    /** Microsoft identity platform device-code endpoints. */
    private object Endpoints {
        const val AUTHORIZE_TEMPLATE = "https://login.microsoftonline.com/%s/oauth2/v2.0/devicecode"
        const val TOKEN_TEMPLATE = "https://login.microsoftonline.com/%s/oauth2/v2.0/token"
        const val SCOPES = "https://outlook.office.com/SMTP.Send offline_access"
    }

    /** Buffer before expiry at which we proactively refresh the token. */
    private val tokenExpiryBufferMs = 60_000L

    private val refreshLocks = HashMap<String, Mutex>()

    /**
     * Step 1: Request a device code from Microsoft. The user must open
     * [DeviceCodeResult.verificationUri] and enter [DeviceCodeResult.userCode].
     */
    override fun requestDeviceCode(clientId: String, tenantId: String): DeviceCodeResult {
        val url = Endpoints.AUTHORIZE_TEMPLATE.format(tenantId)
        val form = FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", Endpoints.SCOPES)
            .build()

        val request = Request.Builder().url(url).post(form).build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string().orEmpty()
            if (!response.isSuccessful) {
                val err = OAuth2Json.decodeErrorResponse(body)
                throw OAuth2Exception("Device code request failed: ${err.error} ${err.description}")
            }
            val parsed = OAuth2Json.decodeDeviceCode(body)
            return DeviceCodeResult(
                deviceCode = parsed.deviceCode,
                userCode = parsed.userCode,
                verificationUri = parsed.verificationUri,
                expiresInMs = parsed.expiresInMs,
                intervalMs = parsed.intervalMs.coerceAtLeast(1000L),
                message = parsed.message,
            )
        }
    }

    /**
     * Step 2: Poll for the token. Returns [DeviceCodePollResult] indicating whether
     * the user has completed authorization, is still pending, or the flow failed.
     */
    override fun pollForToken(
        deviceCode: String,
        clientId: String,
        tenantId: String,
        intervalMs: Long,
    ): DeviceCodePollResult {
        val url = Endpoints.TOKEN_TEMPLATE.format(tenantId)
        val form = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .add("client_id", clientId)
            .add("device_code", deviceCode)
            .build()

        val request = Request.Builder().url(url).post(form).build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string().orEmpty()
            if (response.isSuccessful) {
                val token = OAuth2Json.decodeTokenResponse(body)
                val credentialId = generateCredentialId()
                val nowMs = System.currentTimeMillis()
                val credentials = EmailOAuthCredentials(
                    credentialId = credentialId,
                    clientId = clientId,
                    tenantId = tenantId,
                    refreshToken = token.refreshToken,
                    accessToken = token.accessToken,
                    tokenExpiryMs = nowMs + token.expiresInMs,
                    userEmail = token.email,
                    scopes = token.scopes,
                )
                credentialStore.save(credentials)
                return DeviceCodePollResult.Authorized(credentials)
            }

            val err = OAuth2Json.decodeErrorResponse(body)
            return when (err.error) {
                "authorization_pending" ->
                    DeviceCodePollResult.Pending(intervalMs)
                "slow_down" ->
                    DeviceCodePollResult.Pending((intervalMs + SLOW_DOWN_INCREMENT_MS).coerceAtMost(MAX_POLL_INTERVAL_MS))
                "expired_token", "code_expired" ->
                    DeviceCodePollResult.Failed(err.error, err.description)
                "authorization_declined" ->
                    DeviceCodePollResult.Failed(err.error, err.description)
                else ->
                    DeviceCodePollResult.Failed(err.error, err.description)
            }
        }
    }

    /**
     * Ensure a valid access token is available for the given credential.
     * Refreshes if missing or near expiry. Concurrent refreshes are single-flighted.
     *
     * @return the current valid access token.
     * @throws OAuth2Exception if no credentials exist or the refresh fails.
     */
    suspend fun ensureValidAccessToken(credentialId: String, nowMs: Long): String {
        val credentials = credentialStore.load(credentialId)
            ?: throw OAuth2Exception("No OAuth2 credentials found for sender")

        if (credentials.accessToken.isNotBlank() &&
            credentials.tokenExpiryMs > nowMs + tokenExpiryBufferMs
        ) {
            return credentials.accessToken
        }

        val lock = synchronized(refreshLocks) {
            refreshLocks.getOrPut(credentialId) { Mutex() }
        }
        // Only one coroutine refreshes at a time; others wait and re-read from store.
        lock.withLock {
            // Re-check after acquiring the lock — another coroutine may have refreshed.
            val latest = credentialStore.load(credentialId)
                ?: throw OAuth2Exception("No OAuth2 credentials found for sender")
            if (latest.accessToken.isNotBlank() &&
                latest.tokenExpiryMs > nowMs + tokenExpiryBufferMs
            ) {
                return latest.accessToken
            }
            return refreshToken(latest, nowMs)
        }
    }

    private fun refreshToken(credentials: EmailOAuthCredentials, nowMs: Long): String {
        val url = Endpoints.TOKEN_TEMPLATE.format(credentials.tenantId)
        val form = FormBody.Builder()
            .add("client_id", credentials.clientId)
            .add("refresh_token", credentials.refreshToken)
            .add("grant_type", "refresh_token")
            .add("scope", Endpoints.SCOPES)
            .build()

        val request = Request.Builder().url(url).post(form).build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string().orEmpty()
            if (!response.isSuccessful) {
                val err = OAuth2Json.decodeErrorResponse(body)
                SLog.e(TAG, "OAuth2 token refresh failed: ${err.error} ${err.description}")
                throw OAuth2Exception("OAuth2 token refresh failed: ${err.error}")
            }
            val token = OAuth2Json.decodeTokenResponse(body)
            val newRefreshToken = token.refreshToken.takeIf { it.isNotBlank() }
                ?: credentials.refreshToken
            credentialStore.updateTokens(
                credentialId = credentials.credentialId,
                accessToken = token.accessToken,
                refreshToken = newRefreshToken,
                tokenExpiryMs = nowMs + token.expiresInMs,
            )
            return token.accessToken
        }
    }

    /** Delete the credentials for a sender (e.g. when the sender is removed). */
    override fun deleteCredentials(credentialId: String) {
        synchronized(refreshLocks) { refreshLocks.remove(credentialId) }
        credentialStore.delete(credentialId)
    }

    /** Whether credentials exist for the given [credentialId]. */
    override fun hasCredentials(credentialId: String): Boolean = credentialStore.exists(credentialId)

    private fun generateCredentialId(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(UUID.randomUUID().toString().toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(16)
    }

    class OAuth2Exception(message: String) : RuntimeException(message)

    private companion object {
        const val TAG = "OAuth2Service"
        const val SLOW_DOWN_INCREMENT_MS = 5000L
        const val MAX_POLL_INTERVAL_MS = 60_000L
    }

    // --- Wire JSON DTOs ---

    @Serializable
    private data class DeviceCodeResponse(
        val device_code: String = "",
        val user_code: String = "",
        val verification_uri: String = "",
        val expires_in: Long = 0L,
        val interval: Long = 0L,
        val message: String = "",
        val error: String? = null,
        val error_description: String? = null,
    ) {
        val deviceCode: String get() = device_code
        val userCode: String get() = user_code
        val verificationUri: String get() = verification_uri
        val expiresInMs: Long get() = expires_in.coerceAtLeast(0L) * 1000L
        val intervalMs: Long get() = interval.coerceAtLeast(0L)
    }

    @Serializable
    private data class TokenResponse(
        val access_token: String = "",
        val refresh_token: String = "",
        val expires_in: Long = 0L,
        val scope: String = "",
        val error: String? = null,
        val error_description: String? = null,
    ) {
        val accessToken: String get() = access_token
        val expiresInMs: Long get() = expires_in.coerceAtLeast(0L) * 1000L
        val refreshToken: String get() = refresh_token
        val email: String get() = "" // Not returned by device-code flow
        val scopes: List<String> get() = scope.split(" ").filter { it.isNotBlank() }
    }

    @Serializable
    private data class ErrorResponse(
        val error: String = "",
        val error_description: String = "",
    ) {
        val description: String get() = error_description
    }

    private object OAuth2Json {
        private val format = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

        fun decodeDeviceCode(body: String) = format.decodeFromString<DeviceCodeResponse>(body)
        fun decodeTokenResponse(body: String) = format.decodeFromString<TokenResponse>(body)
        fun decodeErrorResponse(body: String): ErrorResponse =
            runCatching { format.decodeFromString<ErrorResponse>(body) }
                .getOrDefault(ErrorResponse(error = "unknown", error_description = body))
    }
}
