package io.github.magisk317.relay.ui.app

import android.content.Context
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.smscode.runtime.common.process.PhoneProcessRestartCoordinator as SharedCoordinator
import kotlinx.coroutines.CoroutineScope

object PhoneProcessRestartCoordinator {
    private val TARGET_PROCESSES = listOf(
        "com.android.phone",
        "com.xiaomi.phone",
        "com.android.providers.telephony",
        "com.android.mms",
        "com.android.mms:mms_service",
    )

    private val delegate = SharedCoordinator(
        targetProcesses = TARGET_PROCESSES,
        logger = object : SharedCoordinator.Logger {
            override fun info(message: String) = XLog.i(message)

            override fun warn(message: String) = XLog.w(message)
        },
    )

    fun requestAfterInstallOrUpdate(context: Context, scope: CoroutineScope) {
        delegate.requestAfterInstallOrUpdate(context, scope)
    }

    fun restartAfterInstallOrUpdate(context: Context) {
        delegate.restartAfterInstallOrUpdate(context)
    }
}
