package io.github.magisk317.relay.auth

import android.app.Activity

interface GoogleSignInHelper {
    suspend fun signIn(activity: Activity): String
    suspend fun signOut()
}

class GoogleSignInCancelledException(cause: Throwable) : Exception("Google sign-in was cancelled", cause)
