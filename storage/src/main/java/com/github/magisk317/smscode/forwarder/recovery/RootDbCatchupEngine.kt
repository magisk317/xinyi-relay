package com.github.magisk317.smscode.forwarder.recovery

import android.content.Context
import com.github.magisk317.smscode.forwarder.entity.MsgInfo
import com.github.magisk317.smscode.forwarder.utils.SendUtils
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import com.github.magisk317.smscode.common.utils.PrefsReader
import com.github.magisk317.smscode.common.utils.SmsCodeUtils
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.data.db.AppDatabase
import com.github.magisk317.smscode.data.db.dao.SmsMsgDao
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

internal object RootDbCatchupEngine {

    private val SMS_DB_CANDIDATES = listOf(
        "/data/user_de/0/com.android.providers.telephony/databases/mmssms.db",
        "/data/user/0/com.android.providers.telephony/databases/mmssms.db",
        "/data/data/com.android.providers.telephony/databases/mmssms.db",
    )

    private val CALL_DB_CANDIDATES = listOf(
        "/data/user_de/0/com.android.providers.contacts/databases/calllog.db",
        "/data/user/0/com.android.providers.contacts/databases/calllog.db",
        "/data/data/com.android.providers.contacts/databases/calllog.db",
    )

    private const val QUERY_LIMIT = 200
    private const val SQLITE_DB_NOT_FOUND_EXIT_CODE = 3
    private const val SQL_LOG_SNIPPET_LENGTH = 120
    private const val CALL_TYPE_MISSED = 3
    private const val CALL_TYPE_ANSWERED_EXTERNALLY = 7
    private val running = AtomicBoolean(false)

    private data class SmsRow(
        val id: Long,
        val address: String,
        val body: String,
        val date: Long,
    )

    private data class CallRow(
        val id: Long,
        val number: String,
        val date: Long,
        val callType: Int,
    )

    suspend fun runOnce(context: Context, reason: String) {
        val appContext = context.applicationContext ?: context
        if (!running.compareAndSet(false, true)) {
            XLog.d("Root DB catchup skipped: previous run still active reason=%s", reason)
            return
        }
        try {
            runCatching { runCatchup(appContext, reason) }
                .onFailure { throwable ->
                    XLog.w(
                        "Root DB catchup failed: reason=%s err=%s",
                        reason,
                        throwable.message ?: throwable.javaClass.simpleName,
                    )
                }
        } finally {
            running.set(false)
        }
    }

    private suspend fun runCatchup(context: Context, reason: String) {
        if (!PrefsReader.rootDbCatchupEnabled(context)) {
            return
        }
        if (!RootShellExecutor.canUseRoot()) {
            XLog.d("Root DB catchup disabled for this run: su unavailable reason=%s", reason)
            return
        }
        if (!RootShellExecutor.hasSqlite3()) {
            XLog.d("Root DB catchup disabled for this run: sqlite3 unavailable reason=%s", reason)
            return
        }

        val baselineInited = AppPreferencesDataStore.getBoolean(
            context,
            PrefConst.KEY_INTERNAL_ROOT_DB_BASELINE_INITED,
            false,
        )
        if (!baselineInited) {
            initBaseline(context, reason)
            return
        }

        val writeback = PrefsReader.rootDbCatchupWriteback(context)
        val db = AppDatabase.getInstance(context)
        val dao = db.smsMsgDao()

        var lastSmsId = readWatermark(context, PrefConst.KEY_INTERNAL_ROOT_DB_LAST_SMS_ID)
        val smsRows = querySmsRows(lastSmsId)
        for (row in smsRows) {
            lastSmsId = max(lastSmsId, row.id)
            runCatching {
                handleSmsRow(
                    context = context,
                    dao = dao,
                    row = row,
                    writeback = writeback,
                )
            }.onFailure {
                XLog.w(
                    "Root DB catchup sms row failed: id=%d err=%s",
                    row.id,
                    it.message ?: it.javaClass.simpleName,
                )
            }
        }
        writeWatermark(context, PrefConst.KEY_INTERNAL_ROOT_DB_LAST_SMS_ID, lastSmsId)

        var lastCallId = readWatermark(context, PrefConst.KEY_INTERNAL_ROOT_DB_LAST_CALL_ID)
        val callRows = queryCallRows(lastCallId)
        for (row in callRows) {
            lastCallId = max(lastCallId, row.id)
            runCatching {
                handleCallRow(
                    context = context,
                    dao = dao,
                    row = row,
                    writeback = writeback,
                )
            }.onFailure {
                XLog.w(
                    "Root DB catchup call row failed: id=%d err=%s",
                    row.id,
                    it.message ?: it.javaClass.simpleName,
                )
            }
        }
        writeWatermark(context, PrefConst.KEY_INTERNAL_ROOT_DB_LAST_CALL_ID, lastCallId)

        if (smsRows.isNotEmpty() || callRows.isNotEmpty()) {
            XLog.i(
                "Root DB catchup done reason=%s sms=%d call=%d lastSms=%d lastCall=%d",
                reason,
                smsRows.size,
                callRows.size,
                lastSmsId,
                lastCallId,
            )
        }
    }

    private suspend fun initBaseline(context: Context, reason: String) {
        val smsMaxId = queryMaxId(SMS_DB_CANDIDATES, table = "sms")
        val callMaxId = queryMaxId(CALL_DB_CANDIDATES, table = "calls")
        writeWatermark(context, PrefConst.KEY_INTERNAL_ROOT_DB_LAST_SMS_ID, smsMaxId)
        writeWatermark(context, PrefConst.KEY_INTERNAL_ROOT_DB_LAST_CALL_ID, callMaxId)
        AppPreferencesDataStore.setBoolean(
            context,
            PrefConst.KEY_INTERNAL_ROOT_DB_BASELINE_INITED,
            true,
        )
        XLog.i(
            "Root DB catchup baseline initialized reason=%s sms=%d call=%d",
            reason,
            smsMaxId,
            callMaxId,
        )
    }

    private suspend fun handleSmsRow(
        context: Context,
        dao: SmsMsgDao,
        row: SmsRow,
        writeback: Boolean,
    ) {
        val sender = row.address
        val body = row.body
        val date = if (row.date > 0L) row.date else System.currentTimeMillis()
        val smsCode = SmsCodeUtils.parseSmsCodeIfExists(context, body)
        val isCodeSms = smsCode.isNotBlank()

        val canRecord = if (isCodeSms) {
            PrefsReader.recordCodeSmsEnabled(context)
        } else {
            PrefsReader.recordPlainSmsEnabled(context)
        }

        var recordId = dao.getByFingerprint(
            sender = sender,
            body = body,
            date = date,
            msgType = SmsMsg.MSG_TYPE_SMS,
        )?.id

        if (recordId == null && canRecord) {
            trimOldRecordsIfNeeded(
                context = context,
                dao = dao,
                msgType = SmsMsg.MSG_TYPE_SMS,
                isCodeSms = isCodeSms,
            )
            recordId = dao.insert(
                SmsMsg(
                    sender = sender,
                    body = body,
                    date = date,
                    company = "",
                    smsCode = smsCode,
                    msgType = SmsMsg.MSG_TYPE_SMS,
                    callType = 0,
                ),
            )
        }

        val msgInfo = MsgInfo(
            type = "sms",
            from = sender,
            content = body,
            date = Date(date),
            simInfo = "",
        )
        SendUtils.sendMsg(
            context = context,
            msgInfo = msgInfo,
            isCodeSms = isCodeSms,
            recordId = recordId,
            traceId = "root_sms_${row.id}",
        )

        if (writeback) {
            writeBackSmsRead(row.id)
        }
    }

    private suspend fun handleCallRow(
        context: Context,
        dao: SmsMsgDao,
        row: CallRow,
        writeback: Boolean,
    ) {
        val number = row.number.ifBlank { "未知号码" }
        val date = if (row.date > 0L) row.date else System.currentTimeMillis()
        val callLabel = callTypeLabel(row.callType)
        val body = "通话通知：$callLabel\\n号码：$number"

        val canRecord = PrefsReader.recordCallNotifyEnabled(context)
        var recordId = dao.getByFingerprint(
            sender = number,
            body = body,
            date = date,
            msgType = SmsMsg.MSG_TYPE_CALL_NOTIFY,
        )?.id

        if (recordId == null && canRecord) {
            trimOldRecordsIfNeeded(
                context = context,
                dao = dao,
                msgType = SmsMsg.MSG_TYPE_CALL_NOTIFY,
                isCodeSms = false,
            )
            recordId = dao.insert(
                SmsMsg(
                    sender = number,
                    body = body,
                    date = date,
                    company = callLabel,
                    smsCode = "",
                    msgType = SmsMsg.MSG_TYPE_CALL_NOTIFY,
                    callType = row.callType,
                ),
            )
        }

        val msgInfo = MsgInfo(
            type = "call_notify",
            from = number,
            content = body,
            date = Date(date),
            simInfo = callLabel,
            callType = row.callType,
        )
        SendUtils.sendMsg(
            context = context,
            msgInfo = msgInfo,
            isCodeSms = false,
            recordId = recordId,
            traceId = "root_call_${row.id}",
        )

        if (writeback && row.callType == CALL_TYPE_MISSED) {
            writeBackCallNewFlag(row.id)
        }
    }

    private fun trimOldRecordsIfNeeded(
        context: Context,
        dao: SmsMsgDao,
        msgType: Int,
        isCodeSms: Boolean,
    ) {
        val limit = PrefsReader.getHistoryLimit(
            context = context,
            msgType = msgType,
            isCodeSms = isCodeSms,
        )
        if (limit <= 0) return

        val records = dao.getAll()
            .asSequence()
            .filter { record ->
                when (msgType) {
                    SmsMsg.MSG_TYPE_APP_NOTIFY -> record.msgType == SmsMsg.MSG_TYPE_APP_NOTIFY
                    SmsMsg.MSG_TYPE_CALL_NOTIFY -> record.msgType == SmsMsg.MSG_TYPE_CALL_NOTIFY
                    SmsMsg.MSG_TYPE_SMS -> {
                        record.msgType == SmsMsg.MSG_TYPE_SMS &&
                            if (isCodeSms) {
                                !record.smsCode.isNullOrBlank()
                            } else {
                                record.smsCode.isNullOrBlank()
                            }
                    }
                    else -> record.msgType == msgType
                }
            }
            .sortedBy { it.date }
            .toList()

        val removeCount = records.size - limit + 1
        if (removeCount > 0) {
            dao.deleteInTx(records.take(removeCount))
        }
    }

    private fun querySmsRows(watermark: Long): List<SmsRow> {
        val sql =
            "SELECT IFNULL(_id,0),HEX(COALESCE(address,'')),HEX(COALESCE(body,'')),IFNULL(date,0) " +
                "FROM sms WHERE type=1 AND read=0 AND _id>${watermark} ORDER BY _id ASC LIMIT $QUERY_LIMIT;"
        return queryLines(SMS_DB_CANDIDATES, sql)
            .mapNotNull { line ->
                val parts = line.split('|')
                if (parts.size < 4) return@mapNotNull null
                SmsRow(
                    id = parts[0].toLongOrNull() ?: return@mapNotNull null,
                    address = decodeHex(parts[1]),
                    body = decodeHex(parts[2]),
                    date = parts[3].toLongOrNull() ?: 0L,
                )
            }
    }

    private fun queryCallRows(watermark: Long): List<CallRow> {
        val sql =
            "SELECT IFNULL(_id,0),HEX(COALESCE(number,'')),IFNULL(date,0),IFNULL(type,0) " +
                "FROM calls WHERE _id>${watermark} AND type IN (1,2,3,4,5,6,7) ORDER BY _id ASC LIMIT $QUERY_LIMIT;"
        return queryLines(CALL_DB_CANDIDATES, sql)
            .mapNotNull { line ->
                val parts = line.split('|')
                if (parts.size < 4) return@mapNotNull null
                CallRow(
                    id = parts[0].toLongOrNull() ?: return@mapNotNull null,
                    number = decodeHex(parts[1]),
                    date = parts[2].toLongOrNull() ?: 0L,
                    callType = parts[3].toIntOrNull() ?: 0,
                )
            }
    }

    private fun queryMaxId(dbCandidates: List<String>, table: String): Long {
        val sql = "SELECT IFNULL(MAX(_id),0) FROM $table;"
        val line = queryLines(dbCandidates, sql).firstOrNull().orEmpty().trim()
        return line.toLongOrNull() ?: 0L
    }

    private fun queryLines(dbCandidates: List<String>, sql: String): List<String> {
        val command = buildSqliteCommand(dbCandidates, sql, readonly = true)
        val result = RootShellExecutor.run(command)
        if (!result.success) {
            if (result.exitCode != SQLITE_DB_NOT_FOUND_EXIT_CODE) {
                XLog.w(
                    "Root DB catchup sqlite query failed exit=%d sql=%s",
                    result.exitCode,
                    sql.take(SQL_LOG_SNIPPET_LENGTH),
                )
            }
            return emptyList()
        }
        return result.output
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }

    private fun writeBackSmsRead(id: Long) {
        val sql = "UPDATE sms SET read=1 WHERE _id=$id AND read=0;"
        executeWriteSql(SMS_DB_CANDIDATES, sql)
    }

    private fun writeBackCallNewFlag(id: Long) {
        val sql = "UPDATE calls SET \"new\"=0 WHERE _id=$id;"
        executeWriteSql(CALL_DB_CANDIDATES, sql)
    }

    private fun executeWriteSql(dbCandidates: List<String>, sql: String) {
        val command = buildSqliteCommand(dbCandidates, sql, readonly = false)
        val result = RootShellExecutor.run(command)
        if (!result.success && result.exitCode != SQLITE_DB_NOT_FOUND_EXIT_CODE) {
            XLog.w(
                "Root DB catchup sqlite write failed exit=%d sql=%s",
                result.exitCode,
                sql.take(SQL_LOG_SNIPPET_LENGTH),
            )
        }
    }

    private fun buildSqliteCommand(
        dbCandidates: List<String>,
        sql: String,
        readonly: Boolean,
    ): String {
        val candidates = dbCandidates.joinToString(" ") { shellQuote(it) }
        val readonlyArg = if (readonly) "-readonly " else ""
        return buildString {
            append("if ! command -v sqlite3 >/dev/null 2>&1; then exit 127; fi; ")
            append("for db in $candidates; do ")
            append("if [ -f \"${'$'}db\" ]; then sqlite3 ")
            append(readonlyArg)
            append("-separator '|' \"${'$'}db\" ")
            append(shellQuote(sql))
            append("; exit ${'$'}?; fi; ")
            append("done; exit 3")
        }
    }

    private fun shellQuote(input: String): String = "'" + input.replace("'", "'\"'\"'") + "'"

    private suspend fun readWatermark(context: Context, key: String): Long {
        val raw = AppPreferencesDataStore.getString(context, key, "0")
        return raw.toLongOrNull() ?: 0L
    }

    private suspend fun writeWatermark(context: Context, key: String, value: Long) {
        AppPreferencesDataStore.setString(context, key, value.toString())
    }

    private fun decodeHex(hex: String): String {
        val clean = hex.trim()
        if (clean.isEmpty()) return ""
        if (clean.length % 2 != 0) return ""
        val bytes = ByteArray(clean.length / 2)
        var i = 0
        while (i < clean.length) {
            val hi = clean[i].digitToIntOrNull(16) ?: return ""
            val lo = clean[i + 1].digitToIntOrNull(16) ?: return ""
            bytes[i / 2] = ((hi shl 4) + lo).toByte()
            i += 2
        }
        return runCatching { bytes.toString(Charsets.UTF_8) }.getOrDefault("")
    }

    internal fun callTypeLabel(type: Int): String = when (type) {
        1 -> "来电"
        2 -> "去电"
        3 -> "未接"
        4 -> "语音信箱"
        5 -> "拒接"
        6 -> "拦截"
        CALL_TYPE_ANSWERED_EXTERNALLY -> "异地接听"
        else -> "未知类型($type)"
    }
}
