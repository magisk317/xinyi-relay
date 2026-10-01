package io.github.magisk317.relay.sender

import kotlinx.serialization.Serializable

/**
 * Persisted OAuth2 credentials for a single email sender.
 * Secrets are stored outside the sender JSON (which is mirrored and backed up),
 * in an encrypted store keyed by [credentialId].
 */
@Serializable
data class EmailOAuthCredentials(
    val credentialId: String,
    val clientId: String,
    val tenantId: String,
    val refreshToken: String,
    val accessToken: String = "",
    val tokenExpiryMs: Long = 0L,
    val userEmail: String = "",
    val scopes: List<String> = emptyList(),
)

/**
 * Result of a device-code authorization flow.
 */
@Serializable
data class DeviceCodeResult(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInMs: Long,
    val intervalMs: Long,
    val message: String = "",
)

/**
 * Outcome of polling for a device-code token.
 */
sealed interface DeviceCodePollResult {
    /** The user has not yet completed authorization; retry after [retryInMs]. */
    data class Pending(val retryInMs: Long) : DeviceCodePollResult

    /** Authorization succeeded; credentials are returned. */
    data class Authorized(val credentials: EmailOAuthCredentials) : DeviceCodePollResult

    /** The user declined or the code expired. */
    data class Failed(val reason: String, val description: String = "") : DeviceCodePollResult
}

/**
 * Store for OAuth2 credentials that must never enter the sender JSON, remote mirror,
 * or database backup. Implementations must encrypt secrets at rest.
 */
interface EmailOAuthCredentialStore {
    /** Persist or overwrite credentials for the given sender. */
    fun save(credentials: EmailOAuthCredentials)

    /** Load credentials by their stable [credentialId]. Returns null if absent. */
    fun load(credentialId: String): EmailOAuthCredentials?

    /** Update only the token fields after a refresh. No-op if [credentialId] is unknown. */
    fun updateTokens(
        credentialId: String,
        accessToken: String,
        refreshToken: String,
        tokenExpiryMs: Long,
    )

    /** Delete credentials for the given sender (e.g. when the sender is removed). */
    fun delete(credentialId: String)

    /** Whether credentials exist for the given [credentialId]. */
    fun exists(credentialId: String): Boolean
}
