package io.github.magisk317.relay.xp.hook.code.action.impl

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.common.constant.NotificationConst
import io.github.magisk317.relay.common.utils.PrefsReader
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.xp.hook.code.AutoCancelReceiver
import io.github.magisk317.relay.xp.hook.code.CopyCodeReceiver
import io.github.magisk317.relay.xp.hook.code.action.CallableAction

/**
 * 显示验证码通知
 */
class NotifyAction(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg) :
    CallableAction(pluginContext, phoneContext, smsMsg) {

    override fun action(): Bundle? {
        if (PrefsReader.showCodeNotification(mPluginContext)) {
            return showCodeNotification(mSmsMsg)
        }
        return null
    }

    @SuppressLint("UnspecifiedImmutableFlag", "NotificationPermission")
    private fun showCodeNotification(smsMsg: SmsMsg): Bundle? {
        val manager = mPhoneContext.getSystemService(
            Context.NOTIFICATION_SERVICE,
        ) as NotificationManager? ?: return null

        val company = smsMsg.company
        val smsCode = smsMsg.smsCode
        val title = if (TextUtils.isEmpty(company)) smsMsg.sender else company
        val content = mPluginContext.getString(R.string.code_notification_content, smsCode)

        val notificationId = smsMsg.hashCode()

        val copyCodeIntent = CopyCodeReceiver.createIntent(smsCode, notificationId)
        val contentIntent = PendingIntent.getBroadcast(
            mPhoneContext,
            0,
            copyCodeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE or 0x01000000, // PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT
        )

        val builder = NotificationCompat.Builder(mPluginContext, NotificationConst.CHANNEL_ID_RELAY_NOTIFICATION)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setLargeIcon(BitmapFactory.decodeResource(mPluginContext.resources, R.drawable.ic_app_icon))
            .setWhen(System.currentTimeMillis())
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(mPluginContext, R.color.ic_launcher_background))
            .setGroup(NotificationConst.GROUP_KEY_RELAY_NOTIFICATION)

        val autoCancelEnabled = PrefsReader.autoCancelCodeNotification(mPluginContext)
        if (autoCancelEnabled) {
            val retentionTime = PrefsReader.getNotificationRetentionTime(mPluginContext) * 1000L
            if (retentionTime > 0L) {
                builder.setTimeoutAfter(retentionTime)
                scheduleAutoCancel(notificationId, retentionTime)
            } else {
                XLog.i("Auto cancel skipped: retentionTimeMs=%d", retentionTime)
            }
        } else {
            XLog.i("Auto cancel disabled")
        }

        val notification = builder.build()

        manager.notify(notificationId, notification)
        XLog.d("Show notification succeed")

        if (autoCancelEnabled) {
            val retentionTime = PrefsReader.getNotificationRetentionTime(mPluginContext) * 1000L
            val bundle = Bundle()
            bundle.putLong(NOTIFY_RETENTION_TIME, retentionTime)
            bundle.putInt(NOTIFY_ID, notificationId)
            return bundle
        }
        return null
    }

    private fun scheduleAutoCancel(notificationId: Int, retentionTimeMs: Long) {
        val alarmManager = mPluginContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager? ?: return
        val appUid = mPluginContext.applicationInfo?.uid ?: -1
        if (android.os.Process.myUid() != appUid) {
            XLog.i("Skip alarm auto cancel: uid=%d, appUid=%d", android.os.Process.myUid(), appUid)
            return
        }
        val intent = AutoCancelReceiver.createIntent(mPluginContext, notificationId)
        val pendingIntent = PendingIntent.getBroadcast(
            mPluginContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAt = System.currentTimeMillis() + retentionTimeMs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
        XLog.i("Schedule auto cancel alarm, id=%d, delayMs=%d", notificationId, retentionTimeMs)
    }

    companion object {
        const val NOTIFY_RETENTION_TIME = "notify_retention_time"
        const val NOTIFY_ID = "notify_id"
    }
}
