package io.github.magisk317.relay.xp

import android.content.Context
import io.github.magisk317.relay.diagnostics.ActivationDiagnosticsStore
import io.github.magisk317.relay.diagnostics.RuntimeLogStore

object XpHookDiagnostics {
    fun recordSmsHookHeartbeat(
        context: Context,
        packageName: String,
        processName: String,
        source: String,
        verboseLogging: Boolean,
    ) {
        ActivationDiagnosticsStore.recordHookHeartbeat(
            context = context,
            packageName = packageName,
            processName = processName,
            source = source,
            verboseLogging = verboseLogging,
            route = RuntimeLogStore.ROUTE_SMS_HOOK,
        )
    }
}
