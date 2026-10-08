package io.github.magisk317.relay.auth

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NoOpAuthManager : AuthManager {
    override val session: StateFlow<UserSession?> = MutableStateFlow(null)
    override suspend fun signInWithGoogle(idToken: String): Result<UserSession> =
        Result.failure(IllegalStateException("Not available"))
    override suspend fun signOut() = Unit
    override fun isLoggedIn(): Boolean = false
    override fun getCurrentUid(): String? = null
    override suspend fun getGoogleDriveAccessToken(): String? = null
    override fun completeGoogleDriveAuthorization(data: Intent): Nothing =
        throw UnsupportedOperationException("Google Drive authorization is not available")
}
