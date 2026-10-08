package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import io.github.magisk317.relay.xp.hook.SmsHookRuntimeContext
import io.github.magisk317.relay.xp.helper.ModuleConflictArbiter
import io.github.magisk317.relay.xp.helper.SmsCodeConflictNoticeHelper
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.smscode.runtime.verification.SmsHookConstructorInitializer

/**
 * relay wiring for the shared constructor initializer.
 *
 * The flow lives in core; only the host-specific pieces are injected here.
 */
internal fun createRelayConstructorInitializer(
    runtimeInitializer: (Context) -> SmsHookRuntimeContext?,
    notificationChannelInitializer: (SmsHookRuntimeContext) -> Unit,
    copyCodeRegistrar: (SmsHookRuntimeContext) -> Unit,
    heartbeatRecorder: (String) -> Unit,
    suppressionLogger: (String) -> Unit,
    inboxObserverRegistrar: (SmsHookRuntimeContext) -> Unit,
): SmsHookConstructorInitializer<SmsHookRuntimeContext> = SmsHookConstructorInitializer(
    runtimeInitializer = runtimeInitializer,
    conflictNoticeChannelInitializer = SmsCodeConflictNoticeHelper::initNotificationChannel,
    conflictSuppressor = { context, source ->
        ModuleConflictArbiter.shouldSuppressByRelay(context, source)
    },
    showNotificationReader = XpPrefs::showCodeNotification,
    notificationChannelInitializer = notificationChannelInitializer,
    copyCodeRegistrar = copyCodeRegistrar,
    // A hook process must not write ModuleActivationStore: the app-owned process owns
    // that file and persists activation through its own heartbeat. XSC already works
    // this way, so relay keeps the same contract instead of writing from the hook.
    activationMarker = {},
    heartbeatRecorder = heartbeatRecorder,
    suppressionLogger = suppressionLogger,
    inboxObserverRegistrar = inboxObserverRegistrar,
)
