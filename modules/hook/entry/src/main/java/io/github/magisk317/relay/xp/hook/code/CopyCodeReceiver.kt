package io.github.magisk317.relay.xp.hook.code

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.relay.hookentry.R
import io.github.magisk317.relay.xpbridge.XpClipboard
import io.github.magisk317.smscode.runtime.verification.CodeNotificationActionHandler
import io.github.magisk317.smscode.runtime.verification.CodeNotificationActionPayload
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * Receiver for copy code when notification clicked
 */
class CopyCodeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!XpPrefs.mobileAutomationAllowed(context)) {
            MagiskOtel.event(
                name = "sms.copy",
                attributes = mapOf("result" to "skip", "process" to "hook", "reason" to "mobile_entitlement"),
                statusOk = true,
            )
            return
        }
        MagiskOtel.event(
            name = "sms.copy",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "hook",
                "stage" to "receiver",
                "reason" to "copy_click",
            ),
            statusOk = true,
        )
        CodeNotificationActionHandler.handleCopyCodeReceiverIntent(
            context = context,
            intent = intent,
            expectedAction = ACTION_COPY_CODE,
            copyCode = { smsCode ->
                XpClipboard.copyToClipboard(context, smsCode)
                showToast(context, smsCode)
            },
        )
    }

    private fun showToast(context: Context, smsCode: String) {
        val message = context.getString(R.string.prompt_sms_code_copied, smsCode)
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
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
