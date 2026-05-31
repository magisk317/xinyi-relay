@file:Suppress("DEPRECATION")

package io.github.magisk317.relay.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task

class GoogleSignInHelperImpl(context: Context) : GoogleSignInHelper {

    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestScopes(com.google.android.gms.common.api.Scope(com.google.android.gms.common.Scopes.DRIVE_APPFOLDER))
        .requestIdToken(WEB_CLIENT_ID)
        .requestEmail()
        .build()

    private val client: GoogleSignInClient = GoogleSignIn.getClient(context.applicationContext, gso)

    override fun getSignInIntent(): Intent = client.signInIntent

    override fun handleSignInResult(data: Intent?): GoogleSignInAccount? {
        val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
        return try {
            task.getResult(ApiException::class.java)
        } catch (_: ApiException) {
            null
        }
    }

    override fun signOut() {
        client.signOut()
    }

    companion object {
        private const val WEB_CLIENT_ID = "1018461105061-h9ap7k5uof9tl9tdguoqt2d4hkq1efvo.apps.googleusercontent.com"
    }
}
