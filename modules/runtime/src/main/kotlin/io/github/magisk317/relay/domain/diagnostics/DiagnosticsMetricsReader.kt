package io.github.magisk317.relay.domain.diagnostics

import android.content.Context
import io.github.magisk317.forward.db.ForwardDatabase
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.platform.ipc.ForwardDatabaseBootstrapper

/**
 * Business metrics the settings diagnostics page shows. SMS and auto-input
 * metrics remain in the SMS database; forward records and dispatch metrics
 * come exclusively from forward.db.
 */
data class DiagnosticsMetrics(
    val recordCount: Long = 0L,
    val lastSmsAtMs: Long = 0L,
    val lastSmsSimSlot: Int = -1,
    val forward24hTotal: Long = 0L,
    val forward24hSuccess: Long = 0L,
    val forward24hFailed: Long = 0L,
    val lastForwardAtMs: Long = 0L,
    val lastForwardSuccess: Boolean? = null,
    val autoAttempts24h: Long = 0L,
    val autoSuccess24h: Long = 0L,
    val autoFailed24h: Long = 0L,
    val lastAutoAttemptAtMs: Long = 0L,
    val lastAutoSuccess: Boolean? = null,
    val lastAutoFailReason: String = "",
)

object DiagnosticsMetricsReader {
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    suspend fun read(context: Context, nowMs: Long = System.currentTimeMillis()): DiagnosticsMetrics {
        ForwardDatabaseBootstrapper.awaitReady()
        val database = runCatching { AppDatabase.getInstance(context) }.getOrNull()
            ?: return DiagnosticsMetrics()
        val forwardDatabase = runCatching { ForwardDatabase.build(context) }.getOrNull()
            ?: return DiagnosticsMetrics()
        val from24h = nowMs - DAY_MS
        val latestSms = runCatching { forwardDatabase.forwardEventDao().latestSms() }.getOrNull()
        val recordCount = runCatching { forwardDatabase.forwardEventDao().count().toLong() }.getOrDefault(0L)
        val dispatchDao = forwardDatabase.senderDispatchLogDao()
        val latestDispatch = runCatching { dispatchDao.latest() }.getOrNull()
        val forwardTotal = runCatching { dispatchDao.countTotalFrom(from24h) }.getOrDefault(0L)
        val forwardSuccess = runCatching { dispatchDao.countSuccessFrom(from24h) }.getOrDefault(0L)
        val forwardFailed = runCatching { dispatchDao.countFailedFrom(from24h) }.getOrDefault(0L)
        val autoDao = database.autoInputEventDao()
        val latestAuto = runCatching { autoDao.getLatest() }.getOrNull()
        val autoAttempts = runCatching { autoDao.countAttempts(from24h) }.getOrDefault(0L)
        val autoSuccess = runCatching { autoDao.countSuccess(from24h) }.getOrDefault(0L)
        val autoFailed = runCatching { autoDao.countFailed(from24h) }.getOrDefault(0L)
        return DiagnosticsMetrics(
            recordCount = recordCount,
            lastSmsAtMs = latestSms?.date ?: 0L,
            lastSmsSimSlot = latestSms?.simSlot ?: -1,
            forward24hTotal = forwardTotal,
            forward24hSuccess = forwardSuccess,
            forward24hFailed = forwardFailed,
            lastForwardAtMs = latestDispatch?.createdAt ?: 0L,
            lastForwardSuccess = latestDispatch?.success,
            autoAttempts24h = autoAttempts,
            autoSuccess24h = autoSuccess,
            autoFailed24h = autoFailed,
            lastAutoAttemptAtMs = latestAuto?.attemptAt ?: 0L,
            lastAutoSuccess = latestAuto?.success,
            lastAutoFailReason = latestAuto?.failReason.orEmpty(),
        )
    }
}
