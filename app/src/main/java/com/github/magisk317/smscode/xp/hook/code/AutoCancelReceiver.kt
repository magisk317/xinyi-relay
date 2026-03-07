package com.github.magisk317.smscode.xp.hook.code

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.magisk317.smscode.common.utils.XLog

/**
 * Fallback auto-cancel when the module process is not alive.
 */
class AutoCancelReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, NOTIFICATION_NONE)
        if (notificationId == NOTIFICATION_NONE) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        manager?.cancel(notificationId)
        XLog.i("Notification auto cancelled by alarm, id=%d", notificationId)
    }

    companion object {
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        private const val NOTIFICATION_NONE = -0xff

        fun createIntent(context: Context, notificationId: Int): Intent =
            Intent(context, AutoCancelReceiver::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
    }
}
