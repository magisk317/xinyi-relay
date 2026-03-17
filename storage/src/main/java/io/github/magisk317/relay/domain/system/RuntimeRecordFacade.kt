package io.github.magisk317.relay.domain.system

import android.content.Context
import io.github.magisk317.relay.data.db.AppDatabase
import io.github.magisk317.relay.data.db.entity.AutoInputEvent
import io.github.magisk317.relay.data.db.entity.SmsMsg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runtime-only persistence facade for lightweight hook / receiver writebacks.
 */
class RuntimeRecordFacade(
    context: Context,
    private val db: AppDatabase = AppDatabase.getInstance(context),
) {
    suspend fun isDuplicateSms(
        sender: String?,
        body: String?,
        date: Long,
        msgType: Int = SmsMsg.MSG_TYPE_SMS,
    ): Boolean = withContext(Dispatchers.IO) {
        db.smsMsgDao().getByFingerprint(sender, body, date, msgType) != null
    }

    suspend fun findSmsRecordIdByFingerprint(
        sender: String?,
        body: String?,
        date: Long,
        msgType: Int = SmsMsg.MSG_TYPE_SMS,
    ): Long? = withContext(Dispatchers.IO) {
        db.smsMsgDao().getByFingerprint(sender, body, date, msgType)?.id
    }

    suspend fun insertAutoInputAttempt(
        recordId: Long?,
        packageName: String?,
        codeLength: Int,
        attemptAt: Long = System.currentTimeMillis(),
    ): Long = withContext(Dispatchers.IO) {
        db.autoInputEventDao().insert(
            AutoInputEvent(
                recordId = recordId,
                packageName = packageName,
                codeLength = codeLength,
                attemptAt = attemptAt,
            ),
        )
    }

    suspend fun updateAutoInputResult(
        attemptId: Long,
        success: Boolean,
        reason: String?,
    ) = withContext(Dispatchers.IO) {
        db.autoInputEventDao().updateResult(attemptId, success, reason)
    }
}
