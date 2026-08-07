package io.github.magisk317.relay.entitlement

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class CredentialManagerMobileEntitlementGoogleSignIn : MobileEntitlementGoogleSignIn {
    override suspend fun getIdToken(activity: Activity, serverClientId: String, nonce: String): String {
        require(serverClientId.isNotBlank()) { "google_client_id_missing" }
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setServerClientId(serverClientId)
            .setNonce(nonce)
            .build()
        val response = CredentialManager.create(activity).getCredential(
            activity,
            GetCredentialRequest(listOf(option)),
        )
        val credential = response.credential
        require(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
        ) { "google_credential_type_unsupported" }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
}
