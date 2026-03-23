package io.github.magisk317.relay.ui.record

import android.annotation.SuppressLint
import android.content.Context
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.common.utils.JsonUtils
import io.github.magisk317.relay.common.utils.StorageUtils
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.xpbridge.XpSmsMessage
import kotlinx.coroutines.runBlocking
import java.io.*
import java.nio.charset.StandardCharsets

object CodeRecordRestoreManager {
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
        } catch (e: Exception) {
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

    @JvmStatic
    fun exportToFile(context: Context, smsMsg: XpSmsMessage): Boolean {
        return exportToFile(context, smsMsg.toRuntime())
    }

    @JvmStatic
    fun importToDatabase(context: Context): Boolean = try {
        val recordFiles = getRecordFiles(context)
        val smsMsgList = mutableListOf<SmsMsg>()
        recordFiles?.forEach { recordFile ->
            val smsMsg = loadFromFile(recordFile)
            if (smsMsg != null) {
                smsMsgList.add(smsMsg)
                recordFile.delete()
            }
        }

        if (smsMsgList.isNotEmpty()) {
            runBlocking {
                RuntimeGraph.from(context).relayRecordRepository.insertListAndTrim(
                    smsMsgList,
                    PrefConst.MAX_SMS_RECORDS_COUNT_DEFAULT,
                )
            }
            XLog.d("Import code records to database succeed")
        }
        true
    } catch (t: Throwable) {
        XLog.e("Import code records to database failed.", t)
        false
    }

    @JvmStatic
    fun getRecordFiles(context: Context): Array<File>? {
        val filesDir = StorageUtils.getFilesDir(context)
        return filesDir.listFiles { _, name -> name.startsWith(RECORD_FILE_PREFIX) }
    }

    private fun loadFromFile(recordFile: File): SmsMsg? {
        var isr: InputStreamReader? = null
        return try {
            isr = InputStreamReader(FileInputStream(recordFile), StandardCharsets.UTF_8)
            JsonUtils.entityFromJson(isr, SmsMsg::class.java, true)
        } catch (e: FileNotFoundException) {
            XLog.e("", e)
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
