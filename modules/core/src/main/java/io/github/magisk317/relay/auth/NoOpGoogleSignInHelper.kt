package io.github.magisk317.relay.auth

import android.app.Activity
import android.content.Context

class NoOpGoogleSignInHelper(@Suppress("UNUSED_PARAMETER") context: Context) : GoogleSignInHelper {
    override suspend fun signIn(activity: Activity): Nothing =
        throw UnsupportedOperationException("Google sign-in is not available")

    override suspend fun signOut() = Unit
}
