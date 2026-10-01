package io.github.magisk317.relay.desktop.session

import io.github.magisk317.relay.contract.remote.DesktopSessionResponse
import io.github.magisk317.relay.desktop.remote.ConsoleClient
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Keeps the ConsoleClient token in sync with the persisted session. */
class SessionManager(private val store: ProfileStore) {

    fun clientFor(profile: DesktopProfile): ConsoleClient {
        val client = ConsoleClient(profile.baseUrl)
        store.loadSession(profile.id)?.let { client.accessToken = it.accessToken }
        return client
    }

    fun onAuthenticated(profile: DesktopProfile, response: DesktopSessionResponse) {
        store.saveSession(
            DesktopSession(
                profileId = profile.id,
                username = response.username,
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                expiresAt = response.expiresAt,
                refreshExpiresAt = response.refreshExpiresAt,
            ),
        )
    }

    /** Refreshes when the access token expires within [skewMinutes]; returns whether it refreshed. */
    suspend fun refreshIfExpiring(
        profile: DesktopProfile,
        client: ConsoleClient,
        skewMinutes: Long = 2,
    ): Boolean {
        val session = store.loadSession(profile.id) ?: return false
        val expiresAt = runCatching { Instant.parse(session.expiresAt) }.getOrNull() ?: return false
        if (Instant.now().plus(skewMinutes, ChronoUnit.MINUTES).isBefore(expiresAt)) return false
        val refreshed = client.desktopRefresh(session.refreshToken)
        client.accessToken = refreshed.accessToken
        onAuthenticated(profile, refreshed)
        return true
    }

    suspend fun logout(profile: DesktopProfile, client: ConsoleClient) {
        store.loadSession(profile.id)?.let { runCatching { client.desktopLogout(it.refreshToken) } }
        store.clearSession(profile.id)
        client.accessToken = null
    }
}
