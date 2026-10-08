package io.github.magisk317.relay.auth

data class UserSession(
    val uid: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val idToken: String,
)
