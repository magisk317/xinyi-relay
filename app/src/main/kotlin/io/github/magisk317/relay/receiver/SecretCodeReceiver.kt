package io.github.magisk317.relay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.ui.home.MainActivity
import io.github.magisk317.smscode.runtime.common.appentry.SecretCodeEntry

class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SecretCodeEntry.ACTION_SECRET_CODE) return
        SecretCodeEntry.onReceive(context, intent, MainActivity::class.java)
    }
}
