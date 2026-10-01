package io.github.magisk317.relay.sender

/**
 * Public interface for the OAuth2 device-code flow, exposed to the UI layer.
 *
 * The UI depends only on [io.github.magisk317.relay.sender.api] and cannot see
 * the implementation in [io.github.magisk317.relay.sender.OAuth2Service]; this
 * interface bridges the two.
 */
interface EmailOAuthService {
    /**
     * Step 1: Request a device code from Microsoft. The user must open
     * [DeviceCodeResult.verificationUri] and enter [DeviceCodeResult.userCode]
     * in a browser to authorize the app.
     *
     * @throws OAuth2Exception on network or identity-platform errors.
     */
    fun requestDeviceCode(clientId: String, tenantId: String): DeviceCodeResult

    /**
     * Step 2: Poll for the token after [requestDeviceCode]. Returns a
     * [DeviceCodePollResult] indicating whether the user has completed
     * authorization, should retry later, or the flow failed/expired.
     */
    fun pollForToken(
        deviceCode: String,
        clientId: String,
        tenantId: String,
        intervalMs: Long,
    ): DeviceCodePollResult

    /** Whether credentials exist for the given [credentialId]. */
    fun hasCredentials(credentialId: String): Boolean

    /** Delete the credentials for a sender (e.g. when the sender is removed). */
    fun deleteCredentials(credentialId: String)

    class OAuth2Exception(message: String) : RuntimeException(message)
}
