package io.github.magisk317.relay.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NoOpAuthManager : AuthManager {
    override val session: StateFlow<UserSession?> = MutableStateFlow(null)
    override suspend fun signInWithGoogle(account: Any?): Result<UserSession> =
        Result.failure(IllegalStateException("Not available"))
    override fun signOut() {}
    override fun isLoggedIn(): Boolean = false
    override fun getCurrentUid(): String? = null
}
