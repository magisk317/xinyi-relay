package io.github.magisk317.relay.feature.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.magisk317.relay.common.constant.NotificationConst
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking

object SpecialAlertNotifier {
    private const val DEDUP_WINDOW_MS = 5_000L
    private val legacyVibrationPattern = longArrayOf(0L, 200L, 160L, 240L)
    private val recentAlerts = ConcurrentHashMap<String, Long>()
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun notifySmsKeywordAlert(
        context: Context,
        sender: String,
        body: String,
        company: String,
    ) {
        val settings = loadSettings(context)
        if (!settings.smsKeywordEnabled) return
        val matched = KeywordAlertMatcher.firstMatchedKeyword(
            settings.smsKeywordKeywords,
            buildString {
                append(sender)
                append('\n')
                append(body)
            },
        ) ?: return
        val title = sender.ifBlank { context.getString(R.string.special_alert_sms_notification_title) }
        val source = company.ifBlank { context.getString(R.string.special_alert_sms_source_fallback) }
        notifyInternal(
            context = context,
            dedupKey = "sms:$title:$body",
            title = context.getString(R.string.special_alert_sms_notification_title),
            contentTitle = title,
            contentText = body,
            subText = context.getString(R.string.special_alert_keyword_hit, matched, source),
            notifyEnabled = settings.smsKeywordNotificationEnabled,
            soundEnabled = settings.smsKeywordSoundEnabled,
            vibrateEnabled = settings.smsKeywordVibrateEnabled,
            category = NotificationCompat.CATEGORY_MESSAGE,
        )
    }

    fun notifyAppKeywordAlert(
        context: Context,
        appName: String,
        title: String,
        body: String,
    ) {
        val settings = loadSettings(context)
        if (!settings.appKeywordEnabled) return
        val matched = KeywordAlertMatcher.firstMatchedKeyword(
            settings.appKeywordKeywords,
            buildString {
                append(title)
                append('\n')
                append(body)
                append('\n')
                append(appName)
            },
        ) ?: return
        val contentTitle = title.ifBlank { appName.ifBlank { context.getString(R.string.special_alert_app_source_fallback) } }
        val source = appName.ifBlank { context.getString(R.string.special_alert_app_source_fallback) }
        notifyInternal(
            context = context,
            dedupKey = "app:$appName:$title:$body",
            title = context.getString(R.string.special_alert_app_notification_title),
            contentTitle = contentTitle,
            contentText = body.ifBlank { source },
            subText = context.getString(R.string.special_alert_keyword_hit, matched, source),
            notifyEnabled = settings.appKeywordNotificationEnabled,
            soundEnabled = settings.appKeywordSoundEnabled,
            vibrateEnabled = settings.appKeywordVibrateEnabled,
            category = NotificationCompat.CATEGORY_MESSAGE,
        )
    }

    fun notifyIncomingCallAlert(context: Context, display: String) {
        val settings = loadSettings(context)
        if (!settings.callAlertLocalEnabled) return
        val normalizedDisplay = display.ifBlank { context.getString(R.string.call_alert_notification_title) }
        notifyInternal(
            context = context,
            dedupKey = "call:$normalizedDisplay",
            title = context.getString(R.string.call_alert_notification_title),
            contentTitle = normalizedDisplay,
            contentText = context.getString(R.string.call_alert_local_summary),
            subText = context.getString(R.string.special_alert_call_subtitle),
            notifyEnabled = true,
            soundEnabled = true,
            vibrateEnabled = true,
            category = NotificationCompat.CATEGORY_CALL,
        )
    }

    private fun loadSettings(context: Context) = runBlocking {
        RuntimeSettingsCache.getSpecialAlertSettings(
            RuntimeGraph.from(context).settingsRepository,
        )
    }

    private fun notifyInternal(
        context: Context,
        dedupKey: String,
        title: String,
        contentTitle: String,
        contentText: String,
        subText: String,
        notifyEnabled: Boolean,
        soundEnabled: Boolean,
        vibrateEnabled: Boolean,
        category: String,
    ) {
        if (!notifyEnabled && !soundEnabled && !vibrateEnabled) {
            XLog.i("Special alert skipped: all local outputs disabled key=%s", dedupKey)
            return
        }
        if (!shouldNotify(dedupKey)) {
            XLog.i("Special alert deduplicated key=%s", dedupKey)
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            XLog.w("Special alert skipped: notifications disabled key=%s", dedupKey)
            return
        }
        val channelId = ensureChannel(context, soundEnabled = soundEnabled, vibrateEnabled = vibrateEnabled)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setContentTitle(title)
            .setSubText(subText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(contentText)
                    .setBigContentTitle(contentTitle),
            )
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(category)
            .setAutoCancel(true)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (soundEnabled) {
                builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            }
            if (vibrateEnabled) {
                builder.setVibrate(legacyVibrationPattern)
            }
            if (!notifyEnabled) {
                builder.setSilent(true)
            }
        }
        runCatching {
            NotificationManagerCompat.from(context).notify(dedupKey.hashCode(), builder.build())
        }.onFailure { error ->
            XLog.w("Special alert notify failed key=%s err=%s", dedupKey, error.message ?: "unknown")
        }
    }

    private fun shouldNotify(key: String): Boolean {
        val now = System.currentTimeMillis()
        val last = recentAlerts[key]
        if (last != null && now - last < DEDUP_WINDOW_MS) {
            return false
        }
        recentAlerts[key] = now
        recentAlerts.entries.iterator().let { iterator ->
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value > DEDUP_WINDOW_MS * 4) {
                    iterator.remove()
                }
            }
        }
        return true
    }

    private fun ensureChannel(context: Context, soundEnabled: Boolean, vibrateEnabled: Boolean): String {
        val channelId = when {
            soundEnabled && vibrateEnabled -> NotificationConst.CHANNEL_ID_SPECIAL_ALERT_SOUND_VIBRATE
            soundEnabled -> NotificationConst.CHANNEL_ID_SPECIAL_ALERT_SOUND
            vibrateEnabled -> NotificationConst.CHANNEL_ID_SPECIAL_ALERT_VIBRATE
            else -> NotificationConst.CHANNEL_ID_SPECIAL_ALERT_SILENT
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return channelId
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager? ?: return channelId
        if (manager.getNotificationChannel(channelId) != null) {
            return channelId
        }
        val nameRes = when (channelId) {
            NotificationConst.CHANNEL_ID_SPECIAL_ALERT_SOUND_VIBRATE -> R.string.special_alert_channel_sound_vibrate
            NotificationConst.CHANNEL_ID_SPECIAL_ALERT_SOUND -> R.string.special_alert_channel_sound
            NotificationConst.CHANNEL_ID_SPECIAL_ALERT_VIBRATE -> R.string.special_alert_channel_vibrate
            else -> R.string.special_alert_channel_silent
        }
        val channel = NotificationChannel(
            channelId,
            context.getString(nameRes),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.special_alert_channel_description)
            enableLights(true)
            enableVibration(vibrateEnabled)
            vibrationPattern = if (vibrateEnabled) longArrayOf(0L, 200L, 160L, 240L) else longArrayOf(0L)
            if (soundEnabled) {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    audioAttributes,
                )
            } else {
                setSound(null, null)
            }
        }
        manager.createNotificationChannel(channel)
        return channelId
    }
}
