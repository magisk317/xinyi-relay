package io.github.magisk317.relay.domain.system

import android.content.Context
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.smscode.db.entity.SmsMsg
import io.github.magisk317.smscode.runtime.contract.record.SmsMsgRecordData
import io.github.magisk317.smscode.runtime.common.record.CodeRecordFileStore
import java.io.File

/**
 * Thin host facade; export/import, telemetry, and file handling live in the
 * shared [CodeRecordFileStore] (smscode-core runtime) so both hosts stay
 * behaviorally aligned. Post-import trimming is applied by the caller via
 * `insertListAndTrim` with [CodeRecordFileStore.DEFAULT_MAX_RECORDS].
 */
object RuntimeCodeRecordFileStore {
    private val store = CodeRecordFileStore(
        recordSerializer = SmsMsgRecordData.serializer(),
        fileNameFor = { record -> "CodeRecord_" + record.date },
    )

    @JvmStatic
    fun exportToFile(context: Context, smsMsg: SmsMsg): Boolean {
        return store.exportToFile(context, smsMsg.toRecordFileData())
    }

    suspend fun importRecordFiles(
        recordFiles: Array<File>?,
        insertRecords: suspend (List<SmsMsgRecordData>) -> Unit,
        logSuccess: (String) -> Unit = XLog::d,
        logError: (String, Throwable) -> Unit = XLog::e,
    ): Boolean {
        return store.importRecordFiles(recordFiles, insertRecords, logSuccess, logError)
    }

    @JvmStatic
    fun getRecordFiles(context: Context): Array<File>? {
        return store.getRecordFiles(context)
    }
}

/**
 * Converts the stored entity to the file-format record. Field names and defaults of
 * [SmsMsgRecordData] mirror this entity's serialization surface, keeping record files
 * written by earlier builds importable.
 */
internal fun SmsMsg.toRecordFileData(): SmsMsgRecordData = SmsMsgRecordData(
    id = id,
    sender = sender,
    body = body,
    date = date,
    processedTime = processedTime,
    company = company,
    smsCode = smsCode,
    packageName = packageName,
    notifyChannelId = notifyChannelId,
    simSlot = simSlot,
    subId = subId,
    contactName = contactName,
    phoneArea = phoneArea,
    forwardStatus = forwardStatus,
    forwardTarget = forwardTarget,
    forwardMessage = forwardMessage,
    forwardTime = forwardTime,
    msgType = msgType,
    callType = callType,
    sessionKey = sessionKey,
)
