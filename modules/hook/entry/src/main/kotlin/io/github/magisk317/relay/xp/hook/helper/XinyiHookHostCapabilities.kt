package io.github.magisk317.relay.xp.hook.helper

import android.content.Context
import io.github.magisk317.relay.hookentry.BuildConfig
import io.github.magisk317.relay.xpbridge.XpHookDiagnostics
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.relay.xpbridge.XpSmsCodeParser
import io.github.magisk317.relay.xpbridge.XpStringEscaper
import io.github.magisk317.smscode.xposed.hook.telephony.SmsHookCapabilities

/**
 * xinyi 侧的统一宿主能力适配器。
 *
 * core 的共享 hook 层只依赖 [HookHostCapabilities]；xinyi 的运行时桥接是
 * `xpbridge` 门面（[XpPrefs]/[XpStringEscaper]/[XpSmsCodeParser]/[XpHookDiagnostics]），
 * 本对象把这层差异收敛在一处，使 relay 与 XSC 消费同一份契约。
 */
internal object XinyiHookHostCapabilities : SmsHookCapabilities {
    override fun isVerboseLogMode(context: Context): Boolean = XpPrefs.isVerboseLogMode(context)

    override fun isSensitiveDebugLogMode(context: Context): Boolean =
        XpPrefs.isSensitiveDebugLogMode(context)

    override fun escapeCode(value: String): String = XpStringEscaper.escape(value).orEmpty()

    override fun summarizeCode(value: String): String = XpStringEscaper.summarizeCode(value)

    override fun escapeBody(value: String): String = XpStringEscaper.escape(value).orEmpty()

    override fun summarizeBody(value: String): String = XpStringEscaper.summarizeBody(value)

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
        XpHookDiagnostics.recordSmsHookHeartbeat(
            context = context,
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
        val moduleContext = resolveModuleContext(context) ?: return
        XpHookDiagnostics.recordCallNotificationSource(
            context = moduleContext,
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
        val moduleContext = resolveModuleContext(context) ?: return
        XpHookDiagnostics.recordHookInstall(
            context = moduleContext,
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
        val moduleContext = resolveModuleContext(context) ?: return
        XpHookDiagnostics.recordNotifyEvent(
            context = moduleContext,
            source = source,
            packageName = packageName,
            channelId = channelId,
            msgType = msgType,
            skipReason = skipReason,
            verboseLogging = verboseLogging,
        )
    }

    /**
     * The notification hook runs inside system_server, whose context belongs to `android`.
     * The provider lives in the module APK, so the call has to be made through a package
     * context for the module - the same shape the SMS hooks use for their heartbeat.
     */
    @Volatile
    private var cachedModuleContext: Context? = null

    private fun resolveModuleContext(fallback: Context?): Context? {
        cachedModuleContext?.let { return it }
        val base = fallback?.let { it.applicationContext ?: it }
            ?: resolveCurrentApplication()
            ?: resolveSystemContext()
            ?: return null
        val resolved = if (base.packageName == BuildConfig.APPLICATION_ID) {
            base
        } else {
            runCatching {
                base.createPackageContext(
                    BuildConfig.APPLICATION_ID,
                    Context.CONTEXT_IGNORE_SECURITY,
                )
            }.getOrNull()
        }
        return resolved?.also { cachedModuleContext = it }
    }

    private fun resolveCurrentApplication(): Context? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        activityThread.getDeclaredMethod("currentApplication")
            .apply { isAccessible = true }
            .invoke(null) as? Context
    }.getOrNull()

    private fun resolveSystemContext(): Context? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        val current = activityThread.getDeclaredMethod("currentActivityThread")
            .apply { isAccessible = true }
            .invoke(null)
        activityThread.getDeclaredMethod("getSystemContext")
            .apply { isAccessible = true }
            .invoke(current) as? Context
    }.getOrNull()
}
