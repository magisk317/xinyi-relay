package io.github.magisk317.relay.feature.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import io.github.magisk317.relay.android.common.utils.XLog
import kotlin.math.abs

internal object CallLogQueryHelper {
    private const val LOOKBACK_BEFORE_START_MS = 15_000L
    private const val LOOKAHEAD_AFTER_END_MS = 15_000L
    private const val MAX_ROWS = 8
    private const val CALL_TYPE_INCOMING = 1
    private const val CALL_TYPE_OUTGOING = 2

    data class RecentCall(
        val number: String?,
        val callType: Int,
        val startedAt: Long,
    )

    fun findRecentCall(
        context: Context,
        expectedCallType: Int,
        sessionStartedAt: Long,
        endedAt: Long,
    ): RecentCall? {
        if (!canReadCallLog(context)) return null

        val startWindow = (sessionStartedAt - LOOKBACK_BEFORE_START_MS).coerceAtLeast(0L)
        val endWindow = endedAt + LOOKAHEAD_AFTER_END_MS
        return runCatching {
            queryRecentCall(context, expectedCallType, sessionStartedAt, startWindow, endWindow)
        }.onFailure { error ->
            XLog.w("CallLog query failed: %s", error.message ?: error.javaClass.simpleName)
        }.getOrNull()
    }

    fun canReadCallLog(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED
    }

    internal fun bestMatch(
        rows: List<RecentCall>,
        expectedCallType: Int,
        sessionStartedAt: Long,
    ): RecentCall? {
        if (rows.isEmpty()) return null
        val expectedDirection = directionOf(expectedCallType)
        if (expectedDirection == 0) {
            return rows.minByOrNull { abs(it.startedAt - sessionStartedAt) }
        }
        return rows
            .filter { directionOf(it.callType) == expectedDirection }
            .minByOrNull { abs(it.startedAt - sessionStartedAt) }
    }

    private fun queryRecentCall(
        context: Context,
        expectedCallType: Int,
        sessionStartedAt: Long,
        startWindow: Long,
        endWindow: Long,
    ): RecentCall? {
        val rows = mutableListOf<RecentCall>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
            ),
            "${CallLog.Calls.DATE} BETWEEN ? AND ?",
            arrayOf(startWindow.toString(), endWindow.toString()),
            "${CallLog.Calls.DATE} DESC LIMIT $MAX_ROWS",
        )?.use { cursor ->
            val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            while (cursor.moveToNext()) {
                rows += RecentCall(
                    number = cursor.getString(numberIndex)?.ifBlank { null },
                    callType = cursor.getInt(typeIndex),
                    startedAt = cursor.getLong(dateIndex),
                )
            }
        }
        return bestMatch(rows, expectedCallType, sessionStartedAt)
    }

    private fun directionOf(callType: Int): Int = when (callType) {
        CALL_TYPE_OUTGOING -> CALL_TYPE_OUTGOING
        CALL_TYPE_INCOMING -> CALL_TYPE_INCOMING
        else -> if (callType > 0) CALL_TYPE_INCOMING else 0
    }
}
