@file:Suppress("DEPRECATION")

package io.github.magisk317.relay.feature.call

import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager

internal object LegacyCallStateListener {
    fun register(
        manager: TelephonyManager,
        onStateChanged: (state: Int, phoneNumber: String?) -> Unit,
    ): PhoneStateListener {
        val listener = object : PhoneStateListener() {
            @Deprecated("Deprecated in API 31")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                onStateChanged(state, phoneNumber)
            }
        }
        manager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        return listener
    }

    fun unregister(manager: TelephonyManager, listener: Any?) {
        if (listener !is PhoneStateListener) return
        manager.listen(listener, PhoneStateListener.LISTEN_NONE)
    }
}
