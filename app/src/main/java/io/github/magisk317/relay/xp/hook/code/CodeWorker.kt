package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.os.BundleCompat
import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.relay.xp.hook.code.action.impl.SmsParseAction
import io.github.magisk317.smscode.xposed.utils.XLog
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
        val settings = SmsCodePostParseCoordinator.loadSettings(mPluginContext)
        val plan = SmsCodePostParseCoordinator.createParsedSmsPlan(settings, FORWARD_ACTION_DELAY_MS)
        val moduleEnabled = XpPrefs.isEnabled(mPluginContext)
        val verboseLog = XpPrefs.isVerboseLogMode(mPluginContext)
        XLog.w(
            "Diag settings: event_id=%s enabled=%s, verbose=%s, showNotif=%s, autoCancel=%s, " +
            "retentionSec=%d, autoInput=%s, copy=%s, toast=%s, record=%s, " +
            "block=%s, markRead=%s, delete=%s, dedup=%s",
            eventId.ifBlank { "<none>" },
            moduleEnabled,
            verboseLog,
            settings.showNotification,
            settings.autoCancelNotification,
            settings.notificationRetentionMs / 1000L,
            settings.autoInputEnabled,
            settings.copyToClipboardEnabled,
            settings.showToast,
            settings.recordSmsEnabled,
            settings.blockSmsEnabled,
            settings.markAsReadEnabled,
            settings.deleteSmsEnabled,
            settings.deduplicateSmsEnabled,
        )

        if (!moduleEnabled) {
            XLog.w("Diag: module disabled in settings")
            XLog.i("XposedSmsCode disabled, exiting")
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
        smsParseAction.setDeduplicateEnabled(settings.deduplicateSmsEnabled)
        val smsParseFuture = mScheduledExecutor.schedule(smsParseAction, 0, TimeUnit.MILLISECONDS)

        val smsMsg: SmsMsg
        try {
            val parseBundle = smsParseFuture.get()
            if (parseBundle == null) {
                mScheduledExecutor.shutdown()
                return null
            }

            val duplicated = parseBundle.getBoolean(SmsParseAction.SMS_DUPLICATED, false)
            if (duplicated) {
                mScheduledExecutor.shutdown()
                return buildParseResult(plan.blockSms)
            }

            smsMsg = BundleCompat.getParcelable(parseBundle, SmsParseAction.SMS_MSG, SmsMsg::class.java) ?: return null
        } catch (e: Exception) {
            XLog.e("Error occurs when get SmsParseAction call value", e)
            return null
        }

        SmsCodePostParseCoordinator.dispatchParsedSmsActions(
            uiHandler = mUIHandler,
            executor = mScheduledExecutor,
            pluginContext = mPluginContext,
            phoneContext = mPhoneContext,
            smsMsg = smsMsg,
            smsIntent = mSmsIntent,
            eventId = eventId,
            plan = plan,
        )

        mScheduledExecutor.shutdown()
        return buildParseResult(plan.blockSms)
    }

    private fun buildParseResult(blockSms: Boolean): ParseResult {
        val parseResult = ParseResult()
        parseResult.isBlockSms = blockSms
        return parseResult
    }

    companion object {
        private const val FORWARD_ACTION_DELAY_MS = 100L
    }
}
