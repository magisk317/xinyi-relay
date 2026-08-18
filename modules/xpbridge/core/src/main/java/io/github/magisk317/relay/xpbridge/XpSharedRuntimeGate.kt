package io.github.magisk317.relay.xpbridge

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import io.github.magisk317.smscode.runtime.common.ipc.RuntimeStateProviderContract

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
        val normalizedKeys = keys
            .asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .toCollection(ArrayList())
        if (normalizedKeys.isEmpty()) return ClaimResult(claimed = true)

        val extras = Bundle().apply {
            putStringArrayList(RuntimeStateProviderContract.EXTRA_KEYS, normalizedKeys)
            putLong(RuntimeStateProviderContract.EXTRA_WINDOW_MS, windowMs)
            putInt(RuntimeStateProviderContract.EXTRA_MAX_ENTRIES, maxEntries)
        }
        val response = runCatching {
            context.contentResolver.call(
                providerUri(context),
                RuntimeStateProviderContract.METHOD_CLAIM_RUNTIME_GATE,
                fileName,
                extras,
            )
        }.onFailure { error ->
            Log.w(
                LOG_TAG,
                "Runtime gate provider call failed: file=$fileName err=${error.message ?: error.javaClass.simpleName}",
            )
        }.getOrNull()

        if (response == null) {
            // Preserve the historical fail-open behavior: IPC availability must not break SMS
            // delivery. The in-process gates still provide a secondary duplicate guard.
            return ClaimResult(claimed = true)
        }
        val ageMs = response
            .getLong(RuntimeStateProviderContract.RESULT_AGE_MS, RuntimeStateProviderContract.NO_AGE_MS)
            .takeIf { it != RuntimeStateProviderContract.NO_AGE_MS }
        return ClaimResult(
            claimed = response.getBoolean(RuntimeStateProviderContract.RESULT_CLAIMED, true),
            ageMs = ageMs,
            key = response.getString(RuntimeStateProviderContract.RESULT_BLOCKED_KEY),
        )
    }

    private fun providerUri(context: Context): Uri =
        Uri.parse("content://${context.packageName}.db.provider")

    private const val LOG_TAG = "XpSharedRuntimeGate"
    private const val DEFAULT_MAX_ENTRIES = RuntimeStateProviderContract.DEFAULT_MAX_ENTRIES
}
