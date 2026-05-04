package io.github.magisk317.relay.auth

import android.content.Intent

interface GoogleSignInHelper {
    fun getSignInIntent(): Intent
    fun handleSignInResult(data: Intent?): Any?
    fun signOut()
}
