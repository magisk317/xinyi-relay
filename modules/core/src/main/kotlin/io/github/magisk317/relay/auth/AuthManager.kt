package io.github.magisk317.relay.auth

import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

interface AuthManager {
    val session: StateFlow<UserSession?>
    suspend fun signInWithGoogle(idToken: String): Result<UserSession>
    suspend fun signOut()
    fun isLoggedIn(): Boolean
    fun getCurrentUid(): String?
    suspend fun getGoogleDriveAccessToken(): String?
    fun completeGoogleDriveAuthorization(data: Intent): String
}
