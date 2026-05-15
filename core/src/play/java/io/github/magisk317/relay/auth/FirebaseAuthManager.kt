@file:Suppress("DEPRECATION")

package io.github.magisk317.relay.auth

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import io.github.magisk317.relay.android.data.secret.InternalSecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class FirebaseAuthManager(
    private val context: Context,
    private val googleSignInHelper: GoogleSignInHelper,
) : AuthManager {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
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

    override suspend fun signInWithGoogle(account: Any?): Result<UserSession> {
        val gAccount = account as? GoogleSignInAccount
            ?: return Result.failure(IllegalStateException("Invalid account type"))
        return try {
            val credential = GoogleAuthProvider.getCredential(gAccount.idToken, null)
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
        } catch (e: Exception) {
            Timber.e(e, "Google sign-in failed")
            Result.failure(e)
        }
    }

    override fun signOut() {
        auth.signOut()
        googleSignInHelper.signOut()
        _session.value = null
    }

    override fun isLoggedIn(): Boolean = auth.currentUser != null

    override fun getCurrentUid(): String? = auth.currentUser?.uid

    fun observeLoginState(): Flow<UserSession?> = session

    companion object {
        private const val KEY_ID_TOKEN = "firebase_id_token"
        private const val KEY_UID = "firebase_uid"
    }
}
