package io.github.magisk317.relay.data.repository

import android.content.Context
import io.github.magisk317.relay.data.db.AppDatabase
import io.github.magisk317.relay.data.db.dao.SenderDispatchStatRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RuntimeAnalyticsSnapshot(
    val totalMessages: Long,
    val codeDetected: Long,
    val autoInputAttempt: Long,
    val autoInputSuccess: Long,
    val autoInputFailed: Long,
    val forwardTotal: Long,
    val forwardSuccess: Long,
    val forwardFailed: Long,
    val senderStats: List<SenderDispatchStatRow>,
)

data class SenderConfigurationSnapshot(
    val configuredByType: Map<Int, Int>,
    val enabledByType: Map<Int, Int>,
)

class AnalyticsRepository(
    context: Context,
    private val db: AppDatabase = AppDatabase.getInstance(context),
) {
    suspend fun snapshot(fromMs: Long): RuntimeAnalyticsSnapshot = withContext(Dispatchers.IO) {
        val smsMsgDao = db.smsMsgDao()
        val autoInputDao = db.autoInputEventDao()
        val dispatchDao = db.senderDispatchLogDao()
        RuntimeAnalyticsSnapshot(
            totalMessages = smsMsgDao.countFrom(fromMs),
            codeDetected = smsMsgDao.countCodeSmsFrom(fromMs),
            autoInputAttempt = autoInputDao.countAttempts(fromMs),
            autoInputSuccess = autoInputDao.countSuccess(fromMs),
            autoInputFailed = autoInputDao.countFailed(fromMs),
            forwardTotal = dispatchDao.countTotalFrom(fromMs),
            forwardSuccess = dispatchDao.countSuccessFrom(fromMs),
            forwardFailed = dispatchDao.countFailedFrom(fromMs),
            senderStats = dispatchDao.aggregateBySenderType(fromMs),
        )
    }

    suspend fun senderConfigurationSnapshot(): SenderConfigurationSnapshot = withContext(Dispatchers.IO) {
        val senders = db.senderDao().getAll()
        SenderConfigurationSnapshot(
            configuredByType = senders.groupingBy { it.type }.eachCount(),
            enabledByType = senders.groupingBy { it.type }.fold(0) { acc, sender ->
                if (sender.status == 1) acc + 1 else acc
            },
        )
    }
}
