package io.github.magisk317.relay.xp.hook.code

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.hookentry.R
import io.github.magisk317.relay.xpbridge.XpClipboard
import io.github.magisk317.smscode.xposed.utils.XLog

/**
 * Receiver for copy code when notification clicked
 */
class CopyCodeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (ACTION_COPY_CODE == action) {
            val smsCode = intent.getStringExtra(EXTRA_KEY_CODE)
            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

            // cancel notification
            if (notificationId != -1) {
                val manager = context.getSystemService(
                    Context.NOTIFICATION_SERVICE,
                ) as android.app.NotificationManager?
                manager?.cancel(notificationId)
            }
            // copy to clipboard
            smsCode?.let {
                XpClipboard.copyToClipboard(context, it)
                logCopy(context, it)
            }
        }
    }

    private fun logCopy(context: Context, smsCode: String) {
        val message = context.getString(R.string.prompt_sms_code_copied, smsCode)
        XLog.i(message)
    }

    companion object {
        private const val ACTION_COPY_CODE = "io.github.magisk317.relay.ACTION_COPY_CODE"
        private const val EXTRA_KEY_CODE = "extra_key_code"
        private const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        @JvmStatic
        fun createIntent(context: Context, smsCode: String?, notificationId: Int): Intent =
            Intent(context, CopyCodeReceiver::class.java).apply {
                action = ACTION_COPY_CODE
                putExtra(EXTRA_KEY_CODE, smsCode)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
    }
}
