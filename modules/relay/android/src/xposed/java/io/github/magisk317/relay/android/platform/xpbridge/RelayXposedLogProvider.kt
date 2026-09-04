package io.github.magisk317.relay.android.platform.xpbridge

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Binder
import io.github.magisk317.relay.android.common.utils.SensitiveLogPolicy
import io.github.magisk317.relay.android.diagnostics.RuntimeDiagnosticsBridge
import io.github.magisk317.relay.android.platform.ipc.ProviderCallerPolicy
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore
import io.github.magisk317.xposed.logging.BaseXposedLogProvider
import io.github.magisk317.xposed.logging.LogProviderQuotaConfig
import io.github.magisk317.xposed.logging.LogProviderQuotaPolicy
import io.github.magisk317.xposed.logging.XposedLogEvent

class RelayXposedLogProvider : BaseXposedLogProvider() {
    override val ingressPolicy: LogProviderQuotaPolicy = LogProviderQuotaPolicy(
        LogProviderQuotaConfig(
            maxEventsPerWindow = MAX_EVENTS_PER_MINUTE,
            windowMs = RATE_LIMIT_WINDOW_MILLIS,
            maxBytesPerDay = Long.MAX_VALUE,
            maxEventsPerDay = Long.MAX_VALUE,
        ),
    )
    private var quota: PersistentUidQuota? = null

    override val authority: String by lazy {
        val ctx = context ?: return@lazy "unknown.xposed.log"
        "${ctx.packageName}.$AUTHORITY_SUFFIX"
    }

    override fun onCreate(): Boolean {
        val ctx = context?.applicationContext ?: return false
        quota = runCatching {
            PersistentUidQuota(
                store = SharedPreferencesUidQuotaStore(ctx),
                maxEventsPerDay = MAX_EVENTS_PER_DAY,
                maxBytesPerDay = MAX_BYTES_PER_DAY,
                wallClockMillis = System::currentTimeMillis,
            )
        }.getOrNull()
        return true
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val ctx = context?.applicationContext ?: return null
        if (uri.authority != authority || uri.lastPathSegment != "entry") return null

        val raw = values ?: return null
        val callingUid = Binder.getCallingUid()
        val activeQuota = quota ?: return null
        val payloadBytes = runCatching { raw.payloadBytes() }.getOrNull() ?: return null
        if (!activeQuota.tryConsume(callingUid, payloadBytes)) return null

        return super.insert(uri, raw)
    }

    override fun isCallerAllowed(context: Context): Boolean =
        ProviderCallerPolicy.isSelfOrSystemScope(context)

    override fun appendLog(event: XposedLogEvent) {
        val ctx = context?.applicationContext ?: return
        RuntimeDiagnosticsBridge.ensureInstalled()
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
            throwable = event.throwable,
        )
    }

    private fun ContentValues.payloadBytes(): Long {
        var total = 0L
        PAYLOAD_STRING_KEYS.forEach { key ->
            total += getAsString(key)?.toByteArray(Charsets.UTF_8)?.size ?: 0
            if (total > MAX_BYTES_PER_DAY) return total
        }
        return total
    }

    companion object {
        const val AUTHORITY_SUFFIX: String = "xposed.log"

        internal const val MAX_EVENTS_PER_MINUTE = 60
        internal const val MAX_EVENTS_PER_DAY = 5_000
        internal const val MAX_BYTES_PER_DAY = 8L * 1024L * 1024L
        private const val RATE_LIMIT_WINDOW_MILLIS = 60_000L
        private val PAYLOAD_STRING_KEYS = listOf(
            XposedLogEvent.KEY_SOURCE,
            XposedLogEvent.KEY_LEVEL,
            XposedLogEvent.KEY_TAG,
            XposedLogEvent.KEY_MESSAGE,
            XposedLogEvent.KEY_THROWABLE,
            XposedLogEvent.KEY_ROUTE,
            XposedLogEvent.KEY_PACKAGE_NAME,
            XposedLogEvent.KEY_PROCESS_NAME,
        )

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
