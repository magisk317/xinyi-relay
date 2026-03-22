package io.github.magisk317.relay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AutoInputResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AutoInputResultHandler.action) return
        AutoInputResultHandler.handle(context, intent)
    }
}
