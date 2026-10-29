package io.github.magisk317.relay.xp.notification

import io.github.magisk317.forward.capture.ForwardEndpointResolver
import io.github.magisk317.forward.capture.ForwardNotificationDispatcher

/**
 * Product-side wiring for the shared capture pipeline: which module package
 * receives forwarded notifications and which relay classes back the dispatch
 * and recovery paths.
 */
object RelayForwardDispatchConfig {

    const val RECEIVER_CLASS = "io.github.magisk317.relay.platform.ipc.ForwardReceiver"
    const val RECOVERY_SERVICE_CLASS = "io.github.magisk317.relay.service.ForceStopRecoveryService"
    const val RELAUNCH_ACTIVITY_CLASS = "io.github.magisk317.relay.ui.home.MainActivity"

    /** Legacy candidate kept so the hook also finds relay installs without a registered receiver. */
    const val FALLBACK_PACKAGE = "io.github.magisk317.relay"

    val endpointResolver: ForwardEndpointResolver = ForwardEndpointResolver(
        fallbackCandidatePackages = listOf(FALLBACK_PACKAGE),
        receiverClassName = RECEIVER_CLASS,
        recoveryServiceClassName = RECOVERY_SERVICE_CLASS,
        relaunchActivityClassName = RELAUNCH_ACTIVITY_CLASS,
    )

    val dispatcher: ForwardNotificationDispatcher = ForwardNotificationDispatcher(
        recoveryServiceClassName = RECOVERY_SERVICE_CLASS,
        relaunchActivityClassName = RELAUNCH_ACTIVITY_CLASS,
    )
}
