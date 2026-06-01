package io.github.magisk317.relay.domain.system

import android.annotation.SuppressLint
import android.content.Context
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import io.github.magisk317.smscode.runtime.common.utils.JsonUtils
import io.github.magisk317.smscode.runtime.common.utils.StorageUtils
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object RuntimeCodeRecordFileStore {
    private const val RECORD_FILE_PREFIX = "CodeRecord_"

    @SuppressLint("SetWorldWritable", "SetWorldReadable")
    @JvmStatic
    fun exportToFile(context: Context, smsMsg: SmsMsg): Boolean {
        var osw: OutputStreamWriter? = null
        return try {
            val filename = RECORD_FILE_PREFIX + smsMsg.date
            val recordFile = File(StorageUtils.getFilesDir(context), filename)
            osw = OutputStreamWriter(FileOutputStream(recordFile), StandardCharsets.UTF_8)
            JsonUtils.toJson(smsMsg, osw, true)
            StorageUtils.setFileWorldWritable(recordFile, 0)
            true
        } catch (e: IOException) {
            XLog.e("Export code record to file failed", e)
            false
        } catch (e: SecurityException) {
            XLog.e("Export code record to file failed", e)
            false
        } finally {
            try {
                osw?.close()
            } catch (ignored: IOException) {
                // ignore
            }
        }
    }

    fun importRecordFiles(
        recordFiles: Array<File>?,
        insertRecords: suspend (List<SmsMsg>) -> Unit,
        logSuccess: (String) -> Unit = XLog::d,
        logError: (String, Throwable) -> Unit = XLog::e,
    ): Boolean = runCatching {
        val files = recordFiles.orEmpty()
        val smsMsgList = mutableListOf<SmsMsg>()
        val importedFiles = mutableListOf<File>()
        files.forEach { recordFile ->
            val smsMsg = loadFromFile(recordFile, logError)
            if (smsMsg != null) {
                smsMsgList.add(smsMsg)
                importedFiles.add(recordFile)
            }
        }

        if (smsMsgList.isNotEmpty()) {
            runBlocking {
                insertRecords(smsMsgList)
            }
            importedFiles.forEach { it.delete() }
            logSuccess("Import code records to database succeed")
        }
        true
    }.getOrElse { throwable ->
        logError("Import code records to database failed.", throwable)
        false
    }

    @JvmStatic
    fun getRecordFiles(context: Context): Array<File>? {
        val filesDir = StorageUtils.getFilesDir(context)
        return filesDir.listFiles { _, name -> name.startsWith(RECORD_FILE_PREFIX) }
    }

    private fun loadFromFile(
        recordFile: File,
        logError: (String, Throwable) -> Unit = XLog::e,
    ): SmsMsg? {
        var isr: InputStreamReader? = null
        return try {
            isr = InputStreamReader(FileInputStream(recordFile), StandardCharsets.UTF_8)
            JsonUtils.entityFromJson(isr, SmsMsg::class.java, true)
        } catch (e: FileNotFoundException) {
            logError("Code record file missing: ${recordFile.name}", e)
            null
        } catch (e: IOException) {
            logError("Load code record file failed: ${recordFile.name}", e)
            null
        } catch (e: IllegalArgumentException) {
            logError("Load code record file failed: ${recordFile.name}", e)
            null
        } catch (e: SerializationException) {
            logError("Load code record file failed: ${recordFile.name}", e)
            null
        } finally {
            try {
                isr?.close()
            } catch (ignored: IOException) {
                // Safe to ignore
            }
        }
    }
}
