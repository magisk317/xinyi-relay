package io.github.magisk317.relay.entitlement

import android.app.Activity

interface MobileEntitlementGoogleSignIn {
    suspend fun getIdToken(activity: Activity, serverClientId: String, nonce: String): String
}

class UnavailableMobileEntitlementGoogleSignIn : MobileEntitlementGoogleSignIn {
    override suspend fun getIdToken(activity: Activity, serverClientId: String, nonce: String): String =
        error("google_play_sign_in_unavailable")
}
