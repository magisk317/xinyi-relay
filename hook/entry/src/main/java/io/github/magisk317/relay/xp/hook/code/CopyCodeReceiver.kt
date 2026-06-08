package io.github.magisk317.relay.xp.hook.code

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.hookentry.R
import io.github.magisk317.relay.xpbridge.XpClipboard
import io.github.magisk317.smscode.verification.CodeNotificationActionHandler
import io.github.magisk317.smscode.verification.CodeNotificationActionPayload
import io.github.magisk317.smscode.xposed.utils.XLog

/**
 * Receiver for copy code when notification clicked
 */
class CopyCodeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        CodeNotificationActionHandler.handleCopyCodeReceiverIntent(
            context = context,
            intent = intent,
            expectedAction = ACTION_COPY_CODE,
            copyCode = { smsCode ->
                XpClipboard.copyToClipboard(context, smsCode)
                logCopy(context, smsCode)
            },
        )
    }

    private fun logCopy(context: Context, smsCode: String) {
        val message = context.getString(R.string.prompt_sms_code_copied, smsCode)
        XLog.i(message)
    }

    companion object {
        private const val ACTION_COPY_CODE = "io.github.magisk317.relay.ACTION_COPY_CODE"

        @JvmStatic
        fun createIntent(context: Context, smsCode: String?, notificationId: Int): Intent =
            CodeNotificationActionPayload.createCopyCodeIntent(
                context = context,
                receiverClass = CopyCodeReceiver::class.java,
                action = ACTION_COPY_CODE,
                smsCode = smsCode,
                notificationId = notificationId,
            )
    }
}
