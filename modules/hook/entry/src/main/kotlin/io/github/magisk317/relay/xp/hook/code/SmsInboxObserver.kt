package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import io.github.magisk317.relay.xp.hook.helper.XinyiHookHostCapabilities
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.smscode.xposed.hook.telephony.SmsInboxObserver as SharedSmsInboxObserver
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * relay wiring for the shared inbox observer.
 *
 * Scanning, routing repair and telemetry live in core. relay supplies its host
 * capabilities ([XinyiHookHostCapabilities] over the xpbridge facades) and what to
 * do with a scanned record.
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
            XLog.w(message = "Diag observer sms role: event_id=%s", eventId)
        },
    )

    private val delegate = SharedSmsInboxObserver(
        pluginContext = pluginContext,
        phoneContext = phoneContext,
        recordHandler = { record -> observedSmsHandler.handle(record) },
        capabilities = XinyiHookHostCapabilities,
    )

    fun register() {
        delegate.setRoutingRepair { record -> observedSmsHandler.repairRouting(record) }
        delegate.register()
    }

    fun unregister() {
        delegate.unregister()
    }
}
