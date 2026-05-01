package io.github.magisk317.relay.xp.hook.code

import android.content.Intent
import io.github.magisk317.relay.hookentry.BuildConfig
import io.github.magisk317.smscode.verification.CodeNotificationPayload

object CodeNotificationBroadcastContract {
    const val ACTION_SHOW_CODE_NOTIFICATION = "${BuildConfig.APPLICATION_ID}.ACTION_SHOW_CODE_NOTIFICATION"

    const val EXTRA_SENDER = CodeNotificationPayload.EXTRA_SENDER
    const val EXTRA_COMPANY = CodeNotificationPayload.EXTRA_COMPANY
    const val EXTRA_SMS_CODE = CodeNotificationPayload.EXTRA_SMS_CODE
    const val EXTRA_NOTIFICATION_ID = CodeNotificationPayload.EXTRA_NOTIFICATION_ID
    const val EXTRA_AUTO_CANCEL_ENABLED = CodeNotificationPayload.EXTRA_AUTO_CANCEL_ENABLED
    const val EXTRA_RETENTION_TIME_MS = CodeNotificationPayload.EXTRA_RETENTION_TIME_MS
    const val EXTRA_IPC_TOKEN = CodeNotificationPayload.EXTRA_IPC_TOKEN

    fun createIntent(
        sender: String?,
        company: String?,
        smsCode: String?,
        notificationId: Int,
        autoCancelEnabled: Boolean,
        retentionTimeMs: Long,
        token: String?,
    ): Intent =
        CodeNotificationPayload.fillIntent(
            Intent(ACTION_SHOW_CODE_NOTIFICATION).apply {
            setClassName(BuildConfig.APPLICATION_ID, "io.github.magisk317.relay.receiver.CodeNotificationReceiver")
            },
            CodeNotificationPayload.Payload(
                sender = sender,
                company = company,
                smsCode = smsCode,
                notificationId = notificationId,
                autoCancelEnabled = autoCancelEnabled,
                retentionTimeMs = retentionTimeMs,
                token = token,
            ),
        )
}
