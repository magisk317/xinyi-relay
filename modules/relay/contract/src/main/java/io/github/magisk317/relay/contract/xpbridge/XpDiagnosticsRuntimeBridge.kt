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
    )
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
    ) = Unit
}
