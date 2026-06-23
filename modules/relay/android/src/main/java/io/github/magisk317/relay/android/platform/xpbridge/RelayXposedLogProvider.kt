package io.github.magisk317.relay.android.platform.xpbridge

import android.content.Context
import io.github.magisk317.relay.android.common.utils.SensitiveLogPolicy
import io.github.magisk317.relay.android.diagnostics.RuntimeLogStore
import io.github.magisk317.xposed.logging.BaseXposedLogProvider
import io.github.magisk317.xposed.logging.XposedLogEvent

class RelayXposedLogProvider : BaseXposedLogProvider() {

    override val authority: String by lazy {
        val ctx = context ?: return@lazy "unknown.xposed.log"
        "${ctx.packageName}.$AUTHORITY_SUFFIX"
    }

    override fun appendLog(event: XposedLogEvent) {
        val ctx = context?.applicationContext ?: return
        RuntimeLogStore.initialize(ctx, enableDetailedLogs = true)
        val safeMessage = if (event.sensitive) {
            SensitiveLogPolicy.sanitizeLogMessage(event.message)
        } else {
            event.message
        }
        RuntimeLogStore.append(
            priority = priorityFor(event.level),
            tag = event.tag,
            message = safeMessage,
            force = event.force || event.level in FORCE_LEVELS,
            route = event.route?.ifBlank { RuntimeLogStore.ROUTE_APP }
                ?: event.source.ifBlank { RuntimeLogStore.ROUTE_APP },
        )
    }

    companion object {
        const val AUTHORITY_SUFFIX = "xposed.log"

        fun authority(context: Context): String =
            "${context.packageName}.$AUTHORITY_SUFFIX"

        private val FORCE_LEVELS = setOf("W", "E")

        private fun priorityFor(level: String): Int = when (level) {
            "E" -> android.util.Log.ERROR
            "W" -> android.util.Log.WARN
            "I" -> android.util.Log.INFO
            "D" -> android.util.Log.DEBUG
            else -> android.util.Log.VERBOSE
        }
    }
}
