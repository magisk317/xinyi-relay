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
        if (intent?.action == ForceStopRecoveryContract.ACTION_RECOVERY_WAKEUP) {
            ForceStopRecoveryHandler.handle(
                context = this,
                intent = intent,
                tag = TAG,
            )
        }
        stopSelfResult(startId)
        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "ForceStopRecovery"
    }
}
