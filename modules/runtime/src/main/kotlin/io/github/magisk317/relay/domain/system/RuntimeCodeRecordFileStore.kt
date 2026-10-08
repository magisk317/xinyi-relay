package io.github.magisk317.relay.domain.system

import android.content.Context
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.smscode.db.entity.SmsMsg
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
        recordSerializer = SmsMsg.serializer(),
        fileNameFor = { record -> "CodeRecord_" + record.date },
    )

    @JvmStatic
    fun exportToFile(context: Context, smsMsg: SmsMsg): Boolean {
        return store.exportToFile(context, smsMsg)
    }

    suspend fun importRecordFiles(
        recordFiles: Array<File>?,
        insertRecords: suspend (List<SmsMsg>) -> Unit,
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
