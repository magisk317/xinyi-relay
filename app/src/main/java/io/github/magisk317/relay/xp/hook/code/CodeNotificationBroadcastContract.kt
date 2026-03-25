package io.github.magisk317.relay.xp.hook.code

import android.content.Intent
import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.receiver.CodeNotificationReceiver

object CodeNotificationBroadcastContract {
    const val ACTION_SHOW_CODE_NOTIFICATION = "${BuildConfig.APPLICATION_ID}.ACTION_SHOW_CODE_NOTIFICATION"

    const val EXTRA_SENDER = "sender"
    const val EXTRA_COMPANY = "company"
    const val EXTRA_SMS_CODE = "sms_code"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
    const val EXTRA_AUTO_CANCEL_ENABLED = "auto_cancel_enabled"
    const val EXTRA_RETENTION_TIME_MS = "retention_time_ms"
    const val EXTRA_IPC_TOKEN = "ipc_token"

    fun createIntent(
        sender: String?,
        company: String?,
        smsCode: String?,
        notificationId: Int,
        autoCancelEnabled: Boolean,
        retentionTimeMs: Long,
        token: String?,
    ): Intent =
        Intent(ACTION_SHOW_CODE_NOTIFICATION).apply {
            setClassName(BuildConfig.APPLICATION_ID, CodeNotificationReceiver::class.java.name)
            putExtra(EXTRA_SENDER, sender)
            putExtra(EXTRA_COMPANY, company)
            putExtra(EXTRA_SMS_CODE, smsCode)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_AUTO_CANCEL_ENABLED, autoCancelEnabled)
            putExtra(EXTRA_RETENTION_TIME_MS, retentionTimeMs)
            if (!token.isNullOrBlank()) {
                putExtra(EXTRA_IPC_TOKEN, token)
            }
        }
}
