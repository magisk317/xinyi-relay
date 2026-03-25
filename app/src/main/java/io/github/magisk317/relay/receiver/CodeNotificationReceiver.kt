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
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.magisk317.relay.common.utils.NotificationUtils
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.xp.hook.code.AutoCancelReceiver
import io.github.magisk317.relay.xp.hook.code.CodeNotificationBroadcastContract
import io.github.magisk317.relay.xp.hook.code.CopyCodeReceiver
import io.github.magisk317.relay.xpbridge.XpNotificationBridge
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.smscode.xposed.utils.XLog

class CodeNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CodeNotificationBroadcastContract.ACTION_SHOW_CODE_NOTIFICATION) {
            return
        }

        val smsCode = intent.getStringExtra(CodeNotificationBroadcastContract.EXTRA_SMS_CODE).orEmpty()
        if (smsCode.isBlank()) {
            XLog.w("CodeNotificationReceiver ignored blank smsCode")
            return
        }

        val expectedToken = XpPrefs.getIpcToken(context)
        val receivedToken = intent.getStringExtra(CodeNotificationBroadcastContract.EXTRA_IPC_TOKEN)
        val sentFromUid = resolveSentFromUidCompat()
        val tokenMatched = expectedToken.isNotBlank() && receivedToken == expectedToken
        val allowSystemBypass = expectedToken.isBlank() && shouldAllowSmsHookTokenBypass(sentFromUid)
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
        val notificationId = intent.getIntExtra(
            CodeNotificationBroadcastContract.EXTRA_NOTIFICATION_ID,
            smsCode.hashCode(),
        )
        val autoCancelEnabled = intent.getBooleanExtra(
            CodeNotificationBroadcastContract.EXTRA_AUTO_CANCEL_ENABLED,
            false,
        )
        val retentionTimeMs = intent.getLongExtra(
            CodeNotificationBroadcastContract.EXTRA_RETENTION_TIME_MS,
            0L,
        ).coerceAtLeast(0L)
        val company = intent.getStringExtra(CodeNotificationBroadcastContract.EXTRA_COMPANY)
        val sender = intent.getStringExtra(CodeNotificationBroadcastContract.EXTRA_SENDER)
        val title = if (TextUtils.isEmpty(company)) {
            sender?.takeIf { it.isNotBlank() } ?: appContext.getString(R.string.app_name)
        } else {
            company
        }
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
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag(),
        )

        val builder = NotificationCompat.Builder(
            appContext,
            XpNotificationBridge.CHANNEL_ID_RELAY_NOTIFICATION,
        )
            .setSmallIcon(R.drawable.ic_app_icon)
            .setLargeIcon(BitmapFactory.decodeResource(appContext.resources, R.drawable.ic_app_icon))
            .setWhen(System.currentTimeMillis())
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(appContext, R.color.ic_launcher_background))
            .setGroup(XpNotificationBridge.GROUP_KEY_RELAY_NOTIFICATION)

        if (autoCancelEnabled && retentionTimeMs > 0L) {
            builder.setTimeoutAfter(retentionTimeMs)
        }

        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        if (manager == null) {
            XLog.w("CodeNotificationReceiver missing NotificationManager")
            return
        }

        val diagnostics = NotificationUtils.inspectDelivery(
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

        showNotification(manager, notificationId, builder.build())
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
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag(),
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

    private fun pendingIntentImmutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }

    private fun resolveSentFromUidCompat(): Int? {
        if (Build.VERSION.SDK_INT < API_LEVEL_34) return null
        return runCatching { getSentFromUid() }.getOrNull()
    }

    private fun shouldAllowSmsHookTokenBypass(sentFromUid: Int?): Boolean {
        return sentFromUid == SYSTEM_UID ||
            sentFromUid == PHONE_UID ||
            (Build.VERSION.SDK_INT < API_LEVEL_34 && sentFromUid == null)
    }

    private companion object {
        private const val API_LEVEL_34 = 34
        private const val SYSTEM_UID = 1000
        private const val PHONE_UID = 1001
    }
}
