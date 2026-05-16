package io.github.magisk317.relay.ui.auth

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.relay.auth.AuthManager
import io.github.magisk317.relay.auth.GoogleSignInHelper
import io.github.magisk317.relay.auth.UserSession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LoginViewModel(application: Application) : AndroidViewModel(application), KoinComponent {

    private val googleSignInHelper: GoogleSignInHelper by inject()
    private val authManager: AuthManager by inject()

    val session: StateFlow<UserSession?> = authManager.session

    private val _events = MutableSharedFlow<LoginEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()

    fun getSignInIntent(): Intent = googleSignInHelper.getSignInIntent()

    fun handleSignInResult(data: Intent?) {
        val account = googleSignInHelper.handleSignInResult(data)
        if (account == null) {
            _events.tryEmit(LoginEvent.SignInFailed("Google sign-in cancelled"))
            return
        }
        viewModelScope.launch {
            val result = authManager.signInWithGoogle(account)
            result.fold(
                onSuccess = { _events.tryEmit(LoginEvent.SignInSuccess) },
                onFailure = { e -> _events.tryEmit(LoginEvent.SignInFailed(e.message ?: "Unknown error")) },
            )
        }
    }

    fun signOut() {
        authManager.signOut()
    }

    fun isLoggedIn(): Boolean = authManager.isLoggedIn()

    sealed class LoginEvent {
        data object SignInSuccess : LoginEvent()
        data class SignInFailed(val message: String) : LoginEvent()
    }
}
