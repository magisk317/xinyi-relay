package io.github.magisk317.relay.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task

class GoogleSignInHelper(context: Context) {

    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(WEB_CLIENT_ID)
        .requestEmail()
        .build()

    private val client: GoogleSignInClient = GoogleSignIn.getClient(context.applicationContext, gso)

    fun getSignInIntent(): Intent = client.signInIntent

    fun handleSignInResult(data: Intent?): GoogleSignInAccount? {
        val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
        return try {
            task.getResult(ApiException::class.java)
        } catch (_: ApiException) {
            null
        }
    }

    fun signOut() {
        client.signOut()
    }

    companion object {
        // TODO: Replace with your actual web client ID from Firebase Console
        // Firebase Console -> Authentication -> Sign-in method -> Google -> Web client ID
        private const val WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"
    }
}
