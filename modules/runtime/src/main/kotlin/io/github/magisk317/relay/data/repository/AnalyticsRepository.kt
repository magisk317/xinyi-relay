package io.github.magisk317.relay.data.repository

import android.content.Context
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.forward.db.ForwardDatabase
import io.github.magisk317.forward.engine.model.RuntimeAnalyticsSnapshot
import io.github.magisk317.forward.engine.model.SenderConfigurationSnapshot
import io.github.magisk317.forward.engine.service.RuntimeAnalyticsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnalyticsRepository(
    context: Context,
    private val db: AppDatabase = AppDatabase.getInstance(context),
    private val forwardDb: ForwardDatabase = ForwardDatabase.build(context),
) : RuntimeAnalyticsProvider {
    override suspend fun snapshot(fromMs: Long): RuntimeAnalyticsSnapshot = withContext(Dispatchers.IO) {
        val smsMsgDao = db.smsMsgDao()
        val autoInputDao = db.autoInputEventDao()
        val dispatchDao = forwardDb.senderDispatchLogDao()
        RuntimeAnalyticsSnapshot(
            totalMessages = smsMsgDao.countFrom(fromMs),
            codeDetected = smsMsgDao.countCodeSmsFrom(fromMs),
            autoInputAttempt = autoInputDao.countAttempts(fromMs),
            autoInputSuccess = autoInputDao.countSuccess(fromMs),
            autoInputFailed = autoInputDao.countFailed(fromMs),
            forwardTotal = dispatchDao.countTotalFrom(fromMs),
            forwardSuccess = dispatchDao.countSuccessFrom(fromMs),
            forwardFailed = dispatchDao.countFailedFrom(fromMs),
            senderStats = dispatchDao.aggregateBySenderType(fromMs).map(::ForwardDispatchStat),
        )
    }

    override suspend fun senderConfigurationSnapshot(): SenderConfigurationSnapshot = withContext(Dispatchers.IO) {
        val senders = forwardDb.forwardSenderDao().all()
        SenderConfigurationSnapshot(
            configuredByType = senders.groupingBy { it.type }.eachCount(),
            enabledByType = senders.groupingBy { it.type }.fold(0) { acc, sender ->
                if (sender.status == 1) acc + 1 else acc
            },
        )
    }
}

private data class ForwardDispatchStat(
    private val row: io.github.magisk317.forward.db.ForwardSenderDispatchStatRow,
) : io.github.magisk317.forward.engine.model.SenderDispatchStat {
    override val senderType: Int get() = row.senderType
    override val sent: Long get() = row.sent
    override val success: Long get() = row.success
    override val failed: Long get() = row.failed
}
