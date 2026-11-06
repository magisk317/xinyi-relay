package io.github.magisk317.relay.xpbridge

import android.content.Context
import android.util.Log
import io.github.magisk317.xposed.logging.DefaultLogSanitizer
import io.github.magisk317.xposed.logging.MagiskLogPolicy
import io.github.magisk317.xposed.runtime.RuntimeStateProviderClient
import io.github.magisk317.xposed.runtime.RuntimeStateProviderContract
import io.github.magisk317.relay.contract.constant.RelayAppConst

object XpSharedRuntimeGate {
    data class ClaimResult(
        val claimed: Boolean,
        val ageMs: Long? = null,
        val key: String? = null,
    )

    fun claimWithinWindow(
        context: Context,
        fileName: String,
        key: String,
        windowMs: Long,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
    ): ClaimResult = claimAllWithinWindow(
        context = context,
        fileName = fileName,
        keys = listOf(key),
        windowMs = windowMs,
        maxEntries = maxEntries,
    )

    fun claimAllWithinWindow(
        context: Context,
        fileName: String,
        keys: Collection<String>,
        windowMs: Long,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
    ): ClaimResult {
        // The gate protocol lives in the kit, so the relay and the smscode app ask for the same
        // thing in the same way. The kit fails open: a missing IPC channel must not stop the
        // delivery the gate only guards against duplicates.
        val claim = RuntimeStateProviderClient.claimRuntimeGate(
            context = context,
            applicationId = RelayAppConst.APPLICATION_ID,
            fileName = fileName,
            keys = keys,
            windowMs = windowMs,
            maxEntries = maxEntries,
            onFailure = { _, error ->
                if (MagiskLogPolicy.current().loggingEnabled) {
                    Log.w(
                        LOG_TAG,
                        DefaultLogSanitizer.limitForLog(
                            "Runtime gate provider call failed: file=$fileName error=${error.javaClass.simpleName}",
                        ),
                    )
                }
            },
        )
        return ClaimResult(
            claimed = claim.claimed,
            ageMs = claim.ageMs,
            key = claim.blockedKey,
        )
    }

    private const val LOG_TAG = "XpSharedRuntimeGate"
    private const val DEFAULT_MAX_ENTRIES = RuntimeStateProviderContract.DEFAULT_MAX_ENTRIES
}
