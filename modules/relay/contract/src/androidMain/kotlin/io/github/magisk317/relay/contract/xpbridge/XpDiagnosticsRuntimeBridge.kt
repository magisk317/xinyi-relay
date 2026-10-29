package io.github.magisk317.relay.contract.xpbridge

import android.content.Context

interface XpDiagnosticsRuntimeBridge {
    fun appendXposedLog(
        priority: Int,
        tag: String,
        message: String,
        force: Boolean,
        route: String?,
        sensitive: Boolean,
        callerClassName: String?,
    )

    fun bindRuntimeLogContext(
        context: Context,
        verboseLogging: Boolean,
    )

    fun recordSmsHookHeartbeat(
        context: Context,
        packageName: String,
        processName: String,
        source: String,
        verboseLogging: Boolean,
        moduleVersion: Int = 0,
    )

    fun recordCallNotificationSource(
        context: Context,
        packageName: String,
        channelId: String,
        category: String,
        verboseLogging: Boolean,
    ) = Unit

    fun recordHookInstall(
        context: Context,
        hook: String,
        status: String,
        packageName: String,
        processName: String,
        detail: String,
        verboseLogging: Boolean,
    ) = Unit

    fun recordNotifyEvent(
        context: Context,
        source: String,
        packageName: String,
        channelId: String,
        msgType: String,
        skipReason: String,
        verboseLogging: Boolean,
    ) = Unit
}

object NoopXpDiagnosticsRuntimeBridge : XpDiagnosticsRuntimeBridge {
    override fun appendXposedLog(
        priority: Int,
        tag: String,
        message: String,
        force: Boolean,
        route: String?,
        sensitive: Boolean,
        callerClassName: String?,
    ) = Unit

    override fun bindRuntimeLogContext(
        context: Context,
        verboseLogging: Boolean,
    ) = Unit

    override fun recordSmsHookHeartbeat(
        context: Context,
        packageName: String,
        processName: String,
        source: String,
        verboseLogging: Boolean,
        moduleVersion: Int,
    ) = Unit
}
