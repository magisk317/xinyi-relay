package io.github.magisk317.relay.xp.hook.helper

import android.content.Context
import io.github.magisk317.relay.hookentry.BuildConfig
import io.github.magisk317.relay.xp.HookTargetDiagnostics
import io.github.magisk317.relay.xpbridge.XpHookDiagnostics
import io.github.magisk317.relay.xpbridge.XpSmsCodeParser
import io.github.magisk317.relay.xpbridge.XpStringEscaper
import io.github.magisk317.relay.android.prefs.PrefsReader
import io.github.magisk317.smscode.xposed.hook.telephony.SmsHookRuntimeSession
import io.github.magisk317.smscode.xposed.hook.telephony.SmsHookCapabilities
import io.github.magisk317.xposed.logging.XLog

/**
 * xinyi 侧的统一宿主能力适配器。
 *
 * core 的共享 hook 层只依赖 [HookHostCapabilities]；xinyi 的运行时桥接是
 * `xpbridge` 门面（[XpStringEscaper]/[XpSmsCodeParser]/[XpHookDiagnostics]），
 * 本对象把这层差异收敛在一处，使 relay 与 XSC 消费同一份契约。
 */
internal object XinyiHookHostCapabilities : SmsHookCapabilities {
    /** Handler and forward hooks must observe one Context/session lifecycle. */
    internal val smsHookRuntimeSession: SmsHookRuntimeSession = SmsHookRuntimeSession(
        applicationId = BuildConfig.APPLICATION_ID,
        onPhoneContextInitialized = PrefsReader::installSnapshot,
        onPluginContextResolved = { pluginContext, _ ->
            onProcessInit(pluginContext)
        },
        onPhoneContextRecovered = { context ->
            XLog.w(
                message = "SmsHookRuntimeSession: recovered phoneContext from handler instance pkg=%s",
                context.packageName,
            )
        },
        heartbeatRecorder = { pluginContext, phoneContext, packageName, source ->
            recordHookHeartbeat(
                context = pluginContext,
                packageName = packageName,
                processName = phoneContext.applicationInfo?.processName ?: packageName,
                source = source,
                verboseLogging = isVerboseLogMode(pluginContext),
            )
        },
    )
    override fun isVerboseLogMode(context: Context): Boolean =
        BuildConfig.DEBUG || BuildConfig.IS_CI_BUILD

    override fun escapeCode(value: String): String = XpStringEscaper.escape(value).orEmpty()

    override fun escapeBody(value: String): String = XpStringEscaper.escape(value).orEmpty()

    override suspend fun parseSmsCode(context: Context, content: String): String =
        XpSmsCodeParser.parseSmsCodeIfExists(context, content)

    override fun onProcessInit(context: Context) {
        XpHookDiagnostics.bindRuntimeLogContext(context, isVerboseLogMode(context))
    }

    override fun recordHookHeartbeat(
        context: Context,
        packageName: String,
        processName: String,
        source: String,
        verboseLogging: Boolean,
    ) {
        val processContext = resolveProcessContext(context) ?: return
        XpHookDiagnostics.recordSmsHookHeartbeat(
            context = processContext,
            packageName = packageName,
            processName = processName,
            source = source,
            verboseLogging = verboseLogging,
            moduleVersion = BuildConfig.MODULE_VERSION,
        )
    }

    override fun recordCallNotificationSource(
        context: Context,
        packageName: String,
        channelId: String,
        category: String,
        verboseLogging: Boolean,
    ) {
        val processContext = resolveProcessContext(context) ?: return
        XpHookDiagnostics.recordCallNotificationSource(
            context = processContext,
            packageName = packageName,
            channelId = channelId,
            category = category,
            verboseLogging = verboseLogging,
        )
    }

    override fun recordHookInstall(
        context: Context?,
        hook: String,
        status: String,
        packageName: String,
        processName: String,
        detail: String,
        verboseLogging: Boolean,
    ) {
        val processContext = resolveProcessContext(context) ?: return
        XpHookDiagnostics.recordHookInstall(
            context = processContext,
            hook = hook,
            status = status,
            packageName = packageName,
            processName = processName,
            detail = detail,
            verboseLogging = verboseLogging,
        )
    }

    override fun recordNotifyEvent(
        context: Context,
        source: String,
        packageName: String,
        channelId: String,
        msgType: String,
        skipReason: String,
        verboseLogging: Boolean,
    ) {
        val processContext = resolveProcessContext(context) ?: return
        XpHookDiagnostics.recordNotifyEvent(
            context = processContext,
            source = source,
            packageName = packageName,
            channelId = channelId,
            msgType = msgType,
            skipReason = skipReason,
            verboseLogging = verboseLogging,
        )
    }

    /**
     * The provider authority belongs to the module, but ContentResolver must use the package
     * identity of the hooked process so its package matches the process UID.
     */
    @Volatile
    private var cachedProcessContext: Context? = null

    private fun resolveProcessContext(fallback: Context?): Context? {
        cachedProcessContext?.let { return it }
        return HookTargetDiagnostics.resolveProcessContext(fallback)?.also {
            cachedProcessContext = it
        }
    }
}
