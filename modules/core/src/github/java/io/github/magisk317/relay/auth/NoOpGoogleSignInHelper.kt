package io.github.magisk317.relay.auth

import android.content.Context
import android.content.Intent

class NoOpGoogleSignInHelper(context: Context) : GoogleSignInHelper {
    override fun getSignInIntent(): Intent = Intent()
    override fun handleSignInResult(data: Intent?): Nothing? = null
    override fun signOut() {}
}
