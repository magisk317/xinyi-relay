package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.relay.xpbridge.XpSmsCodeParser
import io.github.magisk317.relay.xpbridge.XpStringEscaper
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.smscode.xposed.hook.telephony.SmsInboxObserver as SharedSmsInboxObserver
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * relay wiring for the shared inbox observer.
 *
 * Scanning, routing repair and telemetry live in core. relay supplies how a code is
 * parsed, how sensitive text is redacted, and what to do with a scanned record.
 */
internal class SmsInboxObserver(
    private val pluginContext: Context,
    private val phoneContext: Context,
) {
    private val queryExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val observedSmsHandler = ObservedSmsHandler(
        pluginContext = pluginContext,
        phoneContext = phoneContext,
        actionExecutor = queryExecutor,
        roleStateLogger = { eventId ->
            XLog.w("Diag observer sms role: event_id=%s", eventId)
        },
    )

    private val delegate = SharedSmsInboxObserver(
        pluginContext = pluginContext,
        phoneContext = phoneContext,
        recordHandler = { record -> observedSmsHandler.handle(record) },
        isSensitiveDebugLog = { context -> XpPrefs.isSensitiveDebugLogMode(context) },
        escapeCode = { value -> XpStringEscaper.escape(value).orEmpty() },
        summarizeCode = XpStringEscaper::summarizeCode,
        escapeBody = { value -> XpStringEscaper.escape(value).orEmpty() },
        summarizeBody = XpStringEscaper::summarizeBody,
        parseSmsCode = { context, content -> XpSmsCodeParser.parseSmsCodeIfExists(context, content) },
    )

    fun register() {
        delegate.setRoutingRepair { record -> observedSmsHandler.repairRouting(record) }
        delegate.register()
    }

    fun unregister() {
        delegate.unregister()
    }
}
