package io.github.magisk317.relay.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.data.secret.InternalSecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthManager(
    private val context: Context,
    private val googleSignInHelper: GoogleSignInHelper,
) : AuthManager {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val authorizationClient = Identity.getAuthorizationClient(context.applicationContext)
    private val driveAuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(Scopes.DRIVE_FILE)))
        .build()
    private val _session = MutableStateFlow<UserSession?>(null)
    override val session: StateFlow<UserSession?> = _session.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                user.getIdToken(true).addOnSuccessListener { result ->
                    val token = result.token
                    if (token != null) {
                        _session.value = UserSession(
                            uid = user.uid,
                            email = user.email.orEmpty(),
                            displayName = user.displayName.orEmpty(),
                            photoUrl = user.photoUrl?.toString(),
                            idToken = token,
                        )
                        InternalSecretStore.putString(context, KEY_ID_TOKEN, token)
                        InternalSecretStore.putString(context, KEY_UID, user.uid)
                    }
                }
            } else {
                _session.value = null
                InternalSecretStore.putString(context, KEY_ID_TOKEN, "")
                InternalSecretStore.putString(context, KEY_UID, "")
            }
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<UserSession> {
        if (idToken.isBlank()) {
            return Result.failure(IllegalArgumentException("Google ID token is blank"))
        }
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: return Result.failure(IllegalStateException("User is null"))
            val tokenResult = user.getIdToken(true).await()
            val token = tokenResult.token ?: return Result.failure(IllegalStateException("Token is null"))
            val session = UserSession(
                uid = user.uid,
                email = user.email.orEmpty(),
                displayName = user.displayName.orEmpty(),
                photoUrl = user.photoUrl?.toString(),
                idToken = token,
            )
            _session.value = session
            Result.success(session)
        } catch (error: FirebaseException) {
            XLog.e("Google sign-in failed: %s", error.message ?: error.javaClass.simpleName)
            Result.failure(error)
        } catch (error: IllegalArgumentException) {
            XLog.e("Google sign-in failed: %s", error.message ?: error.javaClass.simpleName)
            Result.failure(error)
        }
    }

    override suspend fun signOut() {
        auth.signOut()
        _session.value = null
        googleSignInHelper.signOut()
    }

    override fun isLoggedIn(): Boolean = auth.currentUser != null

    override fun getCurrentUid(): String? = auth.currentUser?.uid

    override suspend fun getGoogleDriveAccessToken(): String? {
        if (!isLoggedIn()) {
            XLog.w("Google Drive authorization skipped: no signed-in account")
            return null
        }
        val result = authorizationClient.authorize(driveAuthorizationRequest).await()
        if (result.hasResolution()) {
            val pendingIntent = checkNotNull(result.pendingIntent) {
                "Google Drive authorization requires resolution without a PendingIntent"
            }
            throw GoogleDriveAuthorizationRequiredException(pendingIntent)
        }
        return checkNotNull(result.accessToken) {
            "Google Drive authorization completed without an access token"
        }.also {
            XLog.i("Google Drive access token acquired")
        }
    }

    override fun completeGoogleDriveAuthorization(data: Intent): String {
        val result = authorizationClient.getAuthorizationResultFromIntent(data)
        return checkNotNull(result.accessToken) {
            "Google Drive authorization completed without an access token"
        }
    }

    fun observeLoginState(): Flow<UserSession?> = session

    companion object {
        private const val KEY_ID_TOKEN = "firebase_id_token"
        private const val KEY_UID = "firebase_uid"
    }
}
