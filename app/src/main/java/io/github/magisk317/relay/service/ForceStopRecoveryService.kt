package io.github.magisk317.relay.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.github.magisk317.relay.common.utils.RuntimeLogStore
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.forwarder.recovery.RootDbCatchupScheduler

/**
 * Lightweight wake-up service used by system-side Xposed hook to revive app process
 * after force-stop recovery. No foreground UI is started here.
 */
class ForceStopRecoveryService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reason = intent?.getStringExtra(EXTRA_REASON).orEmpty()
        val eventId = intent?.getStringExtra(EXTRA_EVENT_ID).orEmpty()
        XLog.w(
            "ForceStopRecoveryService started. reason=%s event=%s",
            reason.ifBlank { "<none>" },
            eventId.ifBlank { "<none>" },
        )
        RuntimeLogStore.append(
            android.util.Log.WARN,
            TAG,
            "force-stop recovery wakeup reason=${reason.ifBlank { "<none>" }} event=${eventId.ifBlank { "<none>" }}",
            force = true,
        )
        RootDbCatchupScheduler.triggerImmediate(
            context = this,
            reason = "force_stop_recovery",
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
