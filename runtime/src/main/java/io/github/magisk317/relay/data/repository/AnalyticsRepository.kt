package io.github.magisk317.relay.data.repository

import android.content.Context
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.engine.model.RuntimeAnalyticsSnapshot
import io.github.magisk317.relay.engine.model.SenderConfigurationSnapshot
import io.github.magisk317.relay.engine.service.RuntimeAnalyticsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnalyticsRepository(
    context: Context,
    private val db: AppDatabase = AppDatabase.getInstance(context),
) : RuntimeAnalyticsProvider {
    override suspend fun snapshot(fromMs: Long): RuntimeAnalyticsSnapshot = withContext(Dispatchers.IO) {
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

    override suspend fun senderConfigurationSnapshot(): SenderConfigurationSnapshot = withContext(Dispatchers.IO) {
        val senders = db.senderDao().getAll()
        SenderConfigurationSnapshot(
            configuredByType = senders.groupingBy { it.type }.eachCount(),
            enabledByType = senders.groupingBy { it.type }.fold(0) { acc, sender ->
                if (sender.status == 1) acc + 1 else acc
            },
        )
    }
}
