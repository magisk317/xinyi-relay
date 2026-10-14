package io.github.magisk317.relay.xp.hook.helper

import android.content.Context
import io.github.magisk317.relay.xpbridge.XpHookDiagnostics
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.relay.xpbridge.XpSmsCodeParser
import io.github.magisk317.relay.xpbridge.XpStringEscaper
import io.github.magisk317.smscode.xposed.hook.HookHostCapabilities

/**
 * xinyi 侧的统一宿主能力适配器。
 *
 * core 的共享 hook 层只依赖 [HookHostCapabilities]；xinyi 的运行时桥接是
 * `xpbridge` 门面（[XpPrefs]/[XpStringEscaper]/[XpSmsCodeParser]/[XpHookDiagnostics]），
 * 本对象把这层差异收敛在一处，使 relay 与 XSC 消费同一份契约。
 */
internal object XinyiHookHostCapabilities : HookHostCapabilities {
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
        )
    }
}
