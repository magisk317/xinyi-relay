package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.content.Intent
import androidx.core.os.BundleCompat
import io.github.magisk317.relay.hookentry.BuildConfig
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.relay.xp.hook.code.action.impl.SmsParseAction
import io.github.magisk317.smscode.verification.CodeWorker as SharedCodeWorker
import io.github.magisk317.smscode.verification.SmsCodePostParseCoordinator as SharedSmsCodePostParseCoordinator
import io.github.magisk317.smscode.xposed.utils.XLog
import java.util.concurrent.TimeUnit

class CodeWorker(
    private val mPluginContext: Context,
    private val mPhoneContext: Context,
    private val mSmsIntent: Intent,
    private val eventId: String = "",
) {
    fun parse(): ParseResult? {
        return SharedCodeWorker(
            pluginContext = mPluginContext,
            phoneContext = mPhoneContext,
            smsIntent = mSmsIntent,
            eventId = eventId,
            settingsLoader = { context -> SmsCodePostParseCoordinator.loadSettings(context).toShared() },
            moduleEnabledReader = XpPrefs::isEnabled,
            verboseLogReader = XpPrefs::isVerboseLogMode,
            logLevelSetter = XLog::setLogLevel,
            currentLogLevelReader = XLog::getLogLevel,
            defaultLogLevel = BuildConfig.LOG_LEVEL,
            parseRunner = ::runSmsParseAction,
            parsedSmsDispatcher = { uiHandler, executor, pluginContext, phoneContext, smsMsg, eventId, plan ->
                SmsCodePostParseCoordinator.dispatchParsedSmsActions(
                    uiHandler = uiHandler,
                    executor = executor,
                    pluginContext = pluginContext,
                    phoneContext = phoneContext,
                    smsMsg = smsMsg,
                    eventId = eventId,
                    plan = plan.toLocal(),
                )
            },
            parseResultFactory = ::buildParseResult,
        ).parse()
    }

    private fun runSmsParseAction(
        executor: java.util.concurrent.ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsIntent: Intent,
        deduplicateEnabled: Boolean,
    ): SharedCodeWorker.ParseOutcome<SmsMsg>? {
        val smsParseAction = SmsParseAction(pluginContext, phoneContext, null)
        smsParseAction.setSmsIntent(smsIntent)
        smsParseAction.setDeduplicateEnabled(deduplicateEnabled)

        // Submit to executor but wait with a strict timeout to avoid hanging the hook thread.
        val future = executor.submit(java.util.concurrent.Callable {
            smsParseAction.action()
        })
        val parseBundle = try {
            future.get(PARSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            XLog.w("SmsParseAction timed out or failed: %s", e.message ?: e.javaClass.simpleName)
            future.cancel(true)
            null
        } ?: return null

        if (parseBundle.getBoolean(SmsParseAction.SMS_DUPLICATED, false)) {
            return SharedCodeWorker.ParseOutcome(duplicated = true)
        }
        val smsMsg = BundleCompat.getParcelable(parseBundle, SmsParseAction.SMS_MSG, SmsMsg::class.java)
            ?: return null
        return SharedCodeWorker.ParseOutcome(
            smsMsg = smsMsg,
            duplicated = false,
        )
    }

    private fun buildParseResult(blockSms: Boolean): ParseResult {
        return ParseResult().apply { isBlockSms = blockSms }
    }

    private fun SmsCodePostParseCoordinator.Settings.toShared(): SharedSmsCodePostParseCoordinator.Settings {
        return SharedSmsCodePostParseCoordinator.Settings(
            showNotification = showNotification,
            autoCancelNotification = autoCancelNotification,
            notificationRetentionMs = notificationRetentionMs,
            autoInputEnabled = autoInputEnabled,
            autoInputDelayMs = autoInputDelayMs,
            copyToClipboardEnabled = copyToClipboardEnabled,
            showToast = showToast,
            recordSmsEnabled = recordSmsEnabled,
            blockSmsEnabled = blockSmsEnabled,
            markAsReadEnabled = markAsReadEnabled,
            deleteSmsEnabled = deleteSmsEnabled,
            deduplicateSmsEnabled = deduplicateSmsEnabled,
        )
    }

    private fun SharedSmsCodePostParseCoordinator.ParsedSmsPlan.toLocal(): SmsCodePostParseCoordinator.ParsedSmsPlan {
        return SmsCodePostParseCoordinator.ParsedSmsPlan(
            blockSms = blockSms,
            deduplicateSmsEnabled = deduplicateSmsEnabled,
            uiPlan = SmsCodePostParseCoordinator.UiPlan(
                copyToClipboardEnabled = uiPlan.copyToClipboardEnabled,
                showToast = uiPlan.showToast,
            ),
            autoInputDelayMs = autoInputDelayMs,
            notificationPlan = notificationPlan?.let {
                SmsCodePostParseCoordinator.NotificationPlan(autoCancelDelayMs = it.autoCancelDelayMs)
            },
            shouldRecord = shouldRecord,
            operateSmsDelays = operateSmsDelays,
        )
    }

    companion object {
        private const val PARSE_TIMEOUT_MS = 2000L
    }
}
