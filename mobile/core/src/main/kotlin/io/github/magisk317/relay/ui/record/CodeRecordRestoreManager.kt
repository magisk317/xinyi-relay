package io.github.magisk317.relay.ui.record

import android.content.Context
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.relay.android.data.db.legacy.entity.LegacySmsMsg as SmsMsg
import io.github.magisk317.smscode.runtime.contract.record.SmsMsgRecordData
import io.github.magisk317.relay.android.data.mapper.ConfigMapper.toRecordData
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.domain.system.RuntimeCodeRecordFileStore
import io.github.magisk317.smscode.runtime.common.record.CodeRecordFileStore
import java.io.File

object CodeRecordRestoreManager {
    @JvmStatic
    fun exportToFile(context: Context, smsMsg: SmsMsg): Boolean {
        return RuntimeCodeRecordFileStore.exportToFile(context, smsMsg)
    }

    @JvmStatic
    suspend fun importToDatabase(context: Context): Boolean {
        return RuntimeCodeRecordFileStore.importRecordFiles(
            recordFiles = getRecordFiles(context),
            insertRecords = { smsMsgList ->
                RuntimeGraph.from(context).relayRecordRepository.insertListAndTrim(
                    smsMsgList.map { it.toRecordData() },
                    CodeRecordFileStore.DEFAULT_MAX_RECORDS,
                )
            },
        )
    }

    internal suspend fun importRecordFiles(
        recordFiles: Array<File>?,
        insertRecords: suspend (List<SmsMsgRecordData>) -> Unit,
        logSuccess: (String) -> Unit = XLog::d,
        logError: (String, Throwable) -> Unit = XLog::e,
    ): Boolean = RuntimeCodeRecordFileStore.importRecordFiles(
        recordFiles = recordFiles,
        insertRecords = insertRecords,
        logSuccess = logSuccess,
        logError = logError,
    )

    @JvmStatic
    fun getRecordFiles(context: Context): Array<File>? {
        return RuntimeCodeRecordFileStore.getRecordFiles(context)
    }
}
