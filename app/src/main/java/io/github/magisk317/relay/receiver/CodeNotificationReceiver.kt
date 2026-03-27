package io.github.magisk317.relay.receiver

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.xp.hook.code.AutoCancelReceiver
import io.github.magisk317.relay.xp.hook.code.CodeNotificationBroadcastContract
import io.github.magisk317.relay.xp.hook.code.CopyCodeReceiver
import io.github.magisk317.relay.xpbridge.XpNotificationBridge
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.smscode.verification.CodeNotificationDeliveryHelper
import io.github.magisk317.smscode.verification.CodeNotificationPayload
import io.github.magisk317.smscode.xposed.utils.XLog

class CodeNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CodeNotificationBroadcastContract.ACTION_SHOW_CODE_NOTIFICATION) {
            return
        }

        val payload = CodeNotificationPayload.readPayload(intent)
        val smsCode = payload.smsCode.orEmpty()
        if (smsCode.isBlank()) {
            XLog.w("CodeNotificationReceiver ignored blank smsCode")
            return
        }

        val expectedToken = XpPrefs.getIpcToken(context)
        val receivedToken = payload.token
        val sentFromUid = resolveSentFromUidCompat()
        val tokenMatched = expectedToken.isNotBlank() && receivedToken == expectedToken
        val allowSystemBypass = expectedToken.isBlank() && CodeNotificationPayload.shouldAllowSmsHookTokenBypass(sentFromUid)
        if (!tokenMatched && !allowSystemBypass) {
            XLog.w(
                "CodeNotificationReceiver rejected token. expectedEmpty=%s receivedEmpty=%s sentFromUid=%d",
                expectedToken.isBlank(),
                receivedToken.isNullOrBlank(),
                sentFromUid ?: -1,
            )
            return
        }

        val appContext = context.applicationContext
        val notificationId = payload.notificationId
        val autoCancelEnabled = payload.autoCancelEnabled
        val retentionTimeMs = payload.retentionTimeMs
        val title = CodeNotificationPayload.resolveTitle(
            company = payload.company,
            sender = payload.sender,
            fallbackTitle = appContext.getString(R.string.app_name),
        )
        val content = appContext.getString(R.string.code_notification_content, smsCode)

        XpNotificationBridge.createNotificationChannel(
            appContext,
            XpNotificationBridge.CHANNEL_ID_RELAY_NOTIFICATION,
            appContext.getString(R.string.channel_name_relay_notification),
            NotificationManager.IMPORTANCE_HIGH,
        )

        val copyIntent = CopyCodeReceiver.createIntent(appContext, smsCode, notificationId)
        val contentIntent = PendingIntent.getBroadcast(
            appContext,
            notificationId,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or CodeNotificationPayload.pendingIntentImmutableFlag(),
        )

        val notification = CodeNotificationDeliveryHelper.buildCodeNotification(
            context = appContext,
            visualConfig = CodeNotificationDeliveryHelper.VisualConfig(
                channelId = XpNotificationBridge.CHANNEL_ID_RELAY_NOTIFICATION,
                groupKey = XpNotificationBridge.GROUP_KEY_RELAY_NOTIFICATION,
                smallIconResId = R.drawable.ic_app_icon,
                largeIconResId = R.drawable.ic_app_icon,
                accentColorResId = R.color.ic_launcher_background,
            ),
            title = title,
            smsCode = smsCode,
            contentIntent = contentIntent,
            contentTextProvider = { code ->
                appContext.getString(R.string.code_notification_content, code)
            },
            autoCancelEnabled = autoCancelEnabled,
            retentionTimeMs = retentionTimeMs,
        )

        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        if (manager == null) {
            XLog.w("CodeNotificationReceiver missing NotificationManager")
            return
        }

        val diagnostics = XpNotificationBridge.inspectDelivery(
            appContext,
            XpNotificationBridge.CHANNEL_ID_RELAY_NOTIFICATION,
        )
        XLog.i("CodeNotificationReceiver delivery diagnostics: %s", diagnostics.summary())
        if (!diagnostics.canPost) {
            XLog.w(
                "CodeNotificationReceiver posting while app-owned notifications are unavailable: %s",
                diagnostics.summary(),
            )
        }

        showNotification(manager, notificationId, notification)
        XLog.i("CodeNotificationReceiver posted app-owned notification id=%d", notificationId)

        if (autoCancelEnabled && retentionTimeMs > 0L) {
            scheduleAutoCancelSafely(appContext, notificationId, retentionTimeMs)
        }
    }

    @SuppressLint("NotificationPermission")
    private fun showNotification(
        manager: NotificationManager,
        notificationId: Int,
        notification: android.app.Notification,
    ) {
        manager.notify(notificationId, notification)
    }

    private fun scheduleAutoCancelSafely(
        context: Context,
        notificationId: Int,
        retentionTimeMs: Long,
    ) {
        runCatching {
            scheduleAutoCancel(context, notificationId, retentionTimeMs)
        }.onFailure { throwable ->
            XLog.w(
                "CodeNotificationReceiver auto-cancel scheduling failed: id=%d retentionMs=%d err=%s",
                notificationId,
                retentionTimeMs,
                throwable.message ?: throwable.javaClass.simpleName,
            )
        }
    }

    private fun scheduleAutoCancel(
        context: Context,
        notificationId: Int,
        retentionTimeMs: Long,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager? ?: return
        val intent = AutoCancelReceiver.createIntent(context, notificationId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or CodeNotificationPayload.pendingIntentImmutableFlag(),
        )
        val triggerAt = System.currentTimeMillis() + retentionTimeMs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun resolveSentFromUidCompat(): Int? {
        if (Build.VERSION.SDK_INT < API_LEVEL_34) return null
        return runCatching { getSentFromUid() }.getOrNull()
    }

    private companion object {
        private const val API_LEVEL_34 = 34
    }
}
