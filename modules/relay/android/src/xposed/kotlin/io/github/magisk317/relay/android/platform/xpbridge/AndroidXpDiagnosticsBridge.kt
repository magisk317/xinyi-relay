package io.github.magisk317.relay.android.platform.xpbridge

import io.github.magisk317.relay.android.diagnostics.RuntimeDiagnosticsBridge
import android.content.Context
import android.os.Bundle
import android.util.Log
import io.github.magisk317.relay.android.common.utils.SensitiveLogPolicy
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.xposed.runtime.RuntimeStateProviderAddress
import io.github.magisk317.xposed.runtime.RuntimeStateProviderClient
import io.github.magisk317.xposed.runtime.RuntimeStateProviderContract
import io.github.magisk317.relay.android.BuildConfig
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore
import io.github.magisk317.relay.contract.xpbridge.XpDiagnosticsRuntimeBridge

object AndroidXpDiagnosticsBridge : XpDiagnosticsRuntimeBridge {
    override fun appendXposedLog(
        priority: Int,
        tag: String,
        message: String,
        force: Boolean,
        route: String?,
        sensitive: Boolean,
        callerClassName: String?,
    ) {
        val safeMessage = if (sensitive) SensitiveLogPolicy.sanitizeLogMessage(message) else message
        RuntimeDiagnosticsBridge.ensureInstalled()
        RuntimeLogStore.append(
            priority = priority,
            tag = tag,
            message = safeMessage,
            force = force || priority >= Log.WARN,
            route = route ?: RuntimeLogStore.routeFromCallerClassName(callerClassName),
        )
    }

    override fun bindRuntimeLogContext(
        context: Context,
        verboseLogging: Boolean,
    ) {
        RuntimeDiagnosticsBridge.ensureInstalled()
        RuntimeLogStore.initialize(context, enableDetailedLogs = verboseLogging)
    }

    override fun recordSmsHookHeartbeat(
        context: Context,
        packageName: String,
        processName: String,
        source: String,
        verboseLogging: Boolean,
        moduleVersion: Int,
    ) {
        val acknowledged = RuntimeStateProviderClient.recordHookHeartbeat(
            context = context,
            applicationId = BuildConfig.APPLICATION_ID,
            packageName = packageName,
            processName = processName,
            source = source,
            verboseLogging = verboseLogging,
            route = RuntimeLogStore.ROUTE_SMS_HOOK,
            moduleVersion = moduleVersion,
            onFailure = { _, error ->
                XLog.w(
                    message = "Hook heartbeat provider call failed: source=%s err=%s",
                    source,
                    error.message ?: error.javaClass.simpleName,
                )
            },
        )
        if (!acknowledged) {
            XLog.w(message = "Hook heartbeat provider call was not acknowledged: source=%s", source)
        }
    }

    override fun recordHookInstall(
        context: Context,
        hook: String,
        status: String,
        packageName: String,
        processName: String,
        detail: String,
        verboseLogging: Boolean,
    ) {
        val extras = Bundle().apply {
            putString(RuntimeStateProviderContract.EXTRA_HOOK, hook)
            putString(RuntimeStateProviderContract.EXTRA_HOOK_STATUS, status)
            putString(RuntimeStateProviderContract.EXTRA_HOOK_DETAIL, detail)
            putString(RuntimeStateProviderContract.EXTRA_PACKAGE_NAME, packageName)
            putString(RuntimeStateProviderContract.EXTRA_PROCESS_NAME, processName)
        }
        val acknowledged = RuntimeStateProviderAddress.call(
            context = context,
            applicationId = BuildConfig.APPLICATION_ID,
            method = RuntimeStateProviderContract.METHOD_RECORD_HOOK_INSTALL,
            extras = extras,
            onFailure = { _, error ->
                XLog.w(
                    message = "Hook install provider call failed: hook=%s err=%s",
                    hook,
                    error.message ?: error.javaClass.simpleName,
                )
            },
        )?.getBoolean(RuntimeStateProviderContract.RESULT_OK, false) == true
        if (!acknowledged) {
            XLog.w(message = "Hook install provider call was not acknowledged: hook=%s", hook)
        }
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
        val extras = Bundle().apply {
            putString(RuntimeStateProviderContract.EXTRA_NOTIFY_SOURCE, source)
            putString(RuntimeStateProviderContract.EXTRA_PACKAGE_NAME, packageName)
            putString(RuntimeStateProviderContract.EXTRA_CHANNEL_ID, channelId)
            putString(RuntimeStateProviderContract.EXTRA_MSG_TYPE, msgType)
            putString(RuntimeStateProviderContract.EXTRA_SKIP_REASON, skipReason)
        }
        val acknowledged = RuntimeStateProviderAddress.call(
            context = context,
            applicationId = BuildConfig.APPLICATION_ID,
            method = RuntimeStateProviderContract.METHOD_RECORD_NOTIFY_EVENT,
            extras = extras,
            onFailure = { _, error ->
                XLog.w(
                    message = "Notify event provider call failed: pkg=%s err=%s",
                    packageName,
                    error.message ?: error.javaClass.simpleName,
                )
            },
        )?.getBoolean(RuntimeStateProviderContract.RESULT_OK, false) == true
        if (!acknowledged) {
            XLog.w(message = "Notify event provider call was not acknowledged: pkg=%s", packageName)
        }
    }

    override fun recordCallNotificationSource(
        context: Context,
        packageName: String,
        channelId: String,
        category: String,
        verboseLogging: Boolean,
    ) {
        val extras = Bundle().apply {
            putString(RuntimeStateProviderContract.EXTRA_PACKAGE_NAME, packageName)
            putString(RuntimeStateProviderContract.EXTRA_CHANNEL_ID, channelId)
            putString(RuntimeStateProviderContract.EXTRA_CATEGORY, category)
            putBoolean(RuntimeStateProviderContract.EXTRA_VERBOSE_LOGGING, verboseLogging)
        }
        val acknowledged = RuntimeStateProviderAddress.call(
            context = context,
            applicationId = BuildConfig.APPLICATION_ID,
            method = RuntimeStateProviderContract.METHOD_RECORD_CALL_NOTIFICATION_SOURCE,
            extras = extras,
            onFailure = { _, error ->
                XLog.w(
                    message = "Call notify source provider call failed: pkg=%s err=%s",
                    packageName,
                    error.message ?: error.javaClass.simpleName,
                )
            },
        )?.getBoolean(RuntimeStateProviderContract.RESULT_OK, false) == true
        if (!acknowledged) {
            XLog.w(
                message = "Call notify source provider call was not acknowledged: pkg=%s",
                packageName,
            )
        }
    }
}
