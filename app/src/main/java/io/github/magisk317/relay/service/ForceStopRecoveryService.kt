package io.github.magisk317.relay.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Lightweight wake-up service used by system-side Xposed hook to revive app process
 * after force-stop recovery. No foreground UI is started here.
 */
class ForceStopRecoveryService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reason = intent?.getStringExtra(EXTRA_REASON).orEmpty()
        val eventId = intent?.getStringExtra(EXTRA_EVENT_ID).orEmpty()
        ForceStopRecoveryHandler.handle(
            context = this,
            reason = reason,
            eventId = eventId,
            tag = TAG,
        )
        stopSelfResult(startId)
        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "ForceStopRecovery"
        private const val ACTION_NAMESPACE = "io.github.magisk317.relay.action"
        const val ACTION_RECOVERY_WAKEUP = "$ACTION_NAMESPACE.FORCE_STOP_RECOVERY_WAKEUP"
        const val EXTRA_REASON = "reason"
        const val EXTRA_EVENT_ID = "event_id"
    }
}
