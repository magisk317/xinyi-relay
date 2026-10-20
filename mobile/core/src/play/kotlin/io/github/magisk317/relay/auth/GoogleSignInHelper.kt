package io.github.magisk317.relay.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class GoogleSignInHelperImpl(context: Context) : GoogleSignInHelper {

    private val credentialManager = CredentialManager.create(context.applicationContext)

    override suspend fun signIn(activity: Activity): String {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setServerClientId(WEB_CLIENT_ID)
            .build()
        val request = GetCredentialRequest(listOf(googleIdOption))
        val credential = try {
            credentialManager.getCredential(activity, request).credential
        } catch (error: GetCredentialCancellationException) {
            throw GoogleSignInCancelledException(error)
        }

        check(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
        ) { "Unsupported Google credential type: ${credential.type}" }

        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }

    override suspend fun signOut() {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }

    companion object {
        private const val WEB_CLIENT_ID = "1018461105061-h9ap7k5uof9tl9tdguoqt2d4hkq1efvo.apps.googleusercontent.com"
    }
}
