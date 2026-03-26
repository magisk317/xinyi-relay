package io.github.magisk317.relay.xpbridge

import android.content.Context
import io.github.magisk317.relay.common.utils.SharedRuntimeGate

object XpSharedRuntimeGate {
    data class ClaimResult(
        val claimed: Boolean,
        val ageMs: Long? = null,
    )

    fun claimWithinWindow(
        context: Context,
        fileName: String,
        key: String,
        windowMs: Long,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
    ): ClaimResult {
        val result = SharedRuntimeGate.claimWithinWindow(
            context = context,
            fileName = fileName,
            key = key,
            windowMs = windowMs,
            maxEntries = maxEntries,
        )
        return ClaimResult(
            claimed = result.claimed,
            ageMs = result.ageMs,
        )
    }

    private const val DEFAULT_MAX_ENTRIES = 256
}
