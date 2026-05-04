package io.github.magisk317.relay.auth

import kotlinx.coroutines.flow.StateFlow

interface AuthManager {
    val session: StateFlow<UserSession?>
    suspend fun signInWithGoogle(account: Any?): Result<UserSession>
    fun signOut()
    fun isLoggedIn(): Boolean
    fun getCurrentUid(): String?
}
