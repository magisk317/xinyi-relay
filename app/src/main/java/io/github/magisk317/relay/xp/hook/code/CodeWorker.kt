package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.os.BundleCompat
import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.common.utils.PrefsReader
import io.github.magisk317.relay.common.utils.SmsCodeUtils
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.feature.reminder.SpecialAlertCoordinator
import io.github.magisk317.relay.domain.event.RelayEvent
import io.github.magisk317.relay.xp.hook.code.action.impl.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CodeWorker(
    private val mPluginContext: Context,
    private val mPhoneContext: Context,
    private val mSmsIntent: Intent,
    private val eventId: String = "",
) {
    private val mUIHandler: Handler = Handler(Looper.getMainLooper())
    private val mScheduledExecutor = Executors.newSingleThreadScheduledExecutor()

    fun parse(): ParseResult? {
        val moduleEnabled = PrefsReader.isEnabled(mPluginContext)
        val verboseLog = PrefsReader.isVerboseLogMode(mPluginContext)
        val showNotification = PrefsReader.showCodeNotification(mPluginContext)
        val autoCancelNotification = PrefsReader.autoCancelCodeNotification(mPluginContext)
        val retentionSec = PrefsReader.getNotificationRetentionTime(mPluginContext)
        val autoInput = PrefsReader.autoInputCodeEnabled(mPluginContext)
        val copyToClipboard = PrefsReader.copyToClipboardEnabled(mPluginContext)
        val showToast = PrefsReader.shouldShowToast(mPluginContext)
        val recordSms = PrefsReader.recordSmsCodeEnabled(mPluginContext)
        val blockSms = PrefsReader.blockSmsEnabled(mPluginContext)
        val markAsRead = PrefsReader.markAsReadEnabled(mPluginContext)
        val deleteSms = PrefsReader.deleteSmsEnabled(mPluginContext)
        val deduplicateSms = PrefsReader.deduplicateSms(mPluginContext)
        XLog.w(
            "Diag settings: event_id=%s enabled=%s, verbose=%s, showNotif=%s, autoCancel=%s, " +
                "retentionSec=%d, autoInput=%s, copy=%s, toast=%s, record=%s, " +
                "block=%s, markRead=%s, delete=%s, dedup=%s",
            eventId.ifBlank { "<none>" },
            moduleEnabled,
            verboseLog,
            showNotification,
            autoCancelNotification,
            retentionSec,
            autoInput,
            copyToClipboard,
            showToast,
            recordSms,
            blockSms,
            markAsRead,
            deleteSms,
            deduplicateSms,
        )

        if (!moduleEnabled) {
            XLog.w("Diag: module disabled in settings")
            XLog.i("Relay module disabled, exiting")
            return null
        }

        if (verboseLog) {
            XLog.setLogLevel(Log.VERBOSE)
        } else {
            XLog.setLogLevel(BuildConfig.LOG_LEVEL)
        }
        XLog.w(
            "Diag log mode: verboseSetting=%s, activeLevel=%d, defaultLevel=%d",
            verboseLog,
            XLog.getLogLevel(),
            BuildConfig.LOG_LEVEL,
        )

        val smsParseAction = SmsParseAction(mPluginContext, mPhoneContext, null)
        smsParseAction.setSmsIntent(mSmsIntent)
        smsParseAction.setDeduplicateEnabled(deduplicateSms)
        val smsParseFuture = mScheduledExecutor.schedule(smsParseAction, 0, TimeUnit.MILLISECONDS)

        val smsMsg: SmsMsg
        try {
            val parseBundle = smsParseFuture.get()
            if (parseBundle == null) {
                XLog.w(
                    "Diag parse bundle is null: event_id=%s schedulePlainSmsForwardIfNeeded",
                    eventId.ifBlank { "<none>" },
                )
                schedulePlainSmsForwardIfNeeded()
                mScheduledExecutor.shutdown()
                return null
            }

            val duplicated = parseBundle.getBoolean(SmsParseAction.SMS_DUPLICATED, false)
            if (duplicated) {
                XLog.w("Diag duplicated sms skipped: event_id=%s", eventId.ifBlank { "<none>" })
                mScheduledExecutor.shutdown()
                return buildParseResult(blockSms)
            }

            smsMsg = BundleCompat.getParcelable(parseBundle, SmsParseAction.SMS_MSG, SmsMsg::class.java) ?: run {
                XLog.w("Diag parse bundle missing SmsMsg: event_id=%s", eventId.ifBlank { "<none>" })
                return null
            }
        } catch (e: Exception) {
            XLog.e("Error occurs when get SmsParseAction call value", e)
            return null
        }
        XLog.w(
            "Diag parse sms success: event_id=%s sender_hash=%s code_len=%d body_len=%d pkg=%s",
            eventId.ifBlank { "<none>" },
            senderHash(smsMsg.sender),
            smsMsg.smsCode?.length ?: 0,
            smsMsg.body?.length ?: 0,
            smsMsg.packageName ?: "",
        )

        SpecialAlertCoordinator.notifyForEvent(
            context = mPluginContext,
            event = RelayEvent(
                messageType = MessageType.SMS_CODE,
                sourceType = "sms_hook",
                sender = smsMsg.sender.orEmpty(),
                body = smsMsg.body.orEmpty(),
                timestamp = smsMsg.date,
                packageName = smsMsg.packageName.orEmpty(),
                notifyChannelId = "",
                companyOrAppName = smsMsg.company.orEmpty(),
                smsCode = smsMsg.smsCode,
                callType = 0,
                callStage = "",
                simSlot = -1,
                subId = 0,
            ),
            traceId = eventId,
        )

        // 复制到剪切板 Action
        mUIHandler.post(CopyToClipboardAction(mPluginContext, mPhoneContext, smsMsg))

        // 显示Toast Action
        mUIHandler.post(ToastAction(mPluginContext, mPhoneContext, smsMsg))

        val autoInputDelayMs = PrefsReader.getAutoInputCodeDelay(mPluginContext) * 1000L
        // 自动输入 Action
        if (autoInput) {
            XLog.w(
                "Diag auto input scheduled: event_id=%s delayMs=%d code_len=%d pkg=%s",
                eventId.ifBlank { "<none>" },
                autoInputDelayMs,
                smsMsg.smsCode?.length ?: 0,
                smsMsg.packageName ?: "",
            )
            val autoInputAction = AutoInputAction(mPluginContext, mPhoneContext, smsMsg)
            mScheduledExecutor.schedule(autoInputAction, autoInputDelayMs, TimeUnit.MILLISECONDS)
        } else {
            XLog.w("Diag auto input disabled by pref: event_id=%s", eventId.ifBlank { "<none>" })
        }

        // 显示通知 Action
        val notifyAction = NotifyAction(mPluginContext, mPhoneContext, smsMsg)
        val notificationFuture = mScheduledExecutor.schedule(notifyAction, 0, TimeUnit.MILLISECONDS)

        // 记录验证码短信 Action（转发状态与拦截配置解耦）
        val recordSmsAction = RecordSmsAction(mPluginContext, mPhoneContext, smsMsg)
        mScheduledExecutor.schedule(recordSmsAction, 0, TimeUnit.MILLISECONDS)

        // 转发 Action
        val forwardAction = ForwardAction(
            mPluginContext,
            mPhoneContext,
            smsMsg,
            mSmsIntent,
            eventId,
        )
        mScheduledExecutor.schedule(forwardAction, 100, TimeUnit.MILLISECONDS)

        // 操作验证码短信（标记为已读 或者 删除） Action
        scheduleOperateSmsActions(smsMsg)

        var autoCancelRetentionMs = 0L
        if (autoCancelNotification) {
            autoCancelRetentionMs = PrefsReader.getNotificationRetentionTime(mPluginContext) * 1000L
            val notificationId = smsMsg.hashCode()

            val cancelNotifyAction = CancelNotifyAction(mPluginContext, mPhoneContext, smsMsg)
            cancelNotifyAction.setNotificationId(notificationId)

            mScheduledExecutor.schedule(cancelNotifyAction, autoCancelRetentionMs, TimeUnit.MILLISECONDS)
            XLog.d("Scheduled CancelNotifyAction with delay: ${autoCancelRetentionMs}ms for ID: $notificationId")
        }

        mScheduledExecutor.shutdown()
        return buildParseResult(blockSms)
    }

    private fun schedulePlainSmsForwardIfNeeded() {
        val plainSms = runCatching { SmsMsg.fromIntent(mSmsIntent) }.getOrNull() ?: return
        val plainBody = plainSms.body
        if (plainBody.isNullOrBlank()) return
        val company = SmsCodeUtils.parseCompany(plainBody)
            .trim()
            .trim('【', '】', '[', ']')
            .ifBlank { null }
        val packageName = if (company.isNullOrBlank()) {
            null
        } else {
            SmsCodeUtils.findPackageNameByLabel(mPhoneContext, company)
        }
        XLog.w(
            "Diag non-code SMS forwarding triggered: bodyLength=%d",
            plainBody.length,
        )
        SpecialAlertCoordinator.notifyForEvent(
            context = mPluginContext,
            event = RelayEvent(
                messageType = MessageType.SMS_PLAIN,
                sourceType = "sms_hook",
                sender = plainSms.sender.orEmpty(),
                body = plainBody,
                timestamp = plainSms.date,
                packageName = packageName.orEmpty(),
                notifyChannelId = "",
                companyOrAppName = company.orEmpty(),
                smsCode = null,
                callType = 0,
                callStage = "",
                simSlot = -1,
                subId = 0,
            ),
            traceId = eventId,
        )
        val forwardAction = ForwardAction(
            mPluginContext,
            mPhoneContext,
            plainSms.copy(
                smsCode = null,
                company = company,
                packageName = packageName,
            ),
            mSmsIntent,
            eventId,
        )
        mScheduledExecutor.schedule(forwardAction, 100, TimeUnit.MILLISECONDS)
    }

    private fun senderHash(sender: String?): String {
        if (sender.isNullOrBlank()) return "<empty>"
        return sender.takeLast(4).padStart(sender.length.coerceAtMost(4), '*')
    }

    private fun buildParseResult(blockSms: Boolean): ParseResult {
        val parseResult = ParseResult()
        parseResult.isBlockSms = blockSms
        return parseResult
    }

    private fun scheduleOperateSmsActions(smsMsg: SmsMsg) {
        val delays = when {
            PrefsReader.deleteSmsEnabled(mPluginContext) -> DELETE_SMS_DELAYS_MS
            PrefsReader.markAsReadEnabled(mPluginContext) -> MARK_AS_READ_RETRY_DELAYS_MS
            else -> emptyList()
        }
        delays.forEach { delayMs ->
            mScheduledExecutor.schedule(
                OperateSmsAction(mPluginContext, mPhoneContext, smsMsg),
                delayMs,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    companion object {
        private val MARK_AS_READ_RETRY_DELAYS_MS = listOf(300L, 1000L, 2000L)
        private val DELETE_SMS_DELAYS_MS = listOf(300L)
    }
}
