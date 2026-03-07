package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.Manifest
import android.content.ContentValues
import android.database.ContentObserver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.provider.Telephony
import androidx.annotation.IntDef
import androidx.core.content.ContextCompat
import com.github.magisk317.smscode.common.utils.PrefsReader
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.xp.hook.code.action.CallableAction
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * 将验证码短信删除或者标记为已读
 */
class OperateSmsAction(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg) :
    CallableAction(pluginContext, phoneContext, smsMsg) {

    constructor(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        @SmsOp forcedOp: Int,
    ) : this(pluginContext, phoneContext, smsMsg) {
        this.forcedOp = forcedOp
    }

    @IntDef(OP_DELETE, OP_MARK_AS_READ)
    @Retention(AnnotationRetention.SOURCE)
    private annotation class SmsOp

    @SmsOp
    private var forcedOp: Int? = null

    override fun action(): Bundle? {
        val sender = mSmsMsg.sender
        val body = mSmsMsg.body
        when (forcedOp) {
            OP_DELETE -> deleteSms(sender, body)
            OP_MARK_AS_READ -> markSmsAsRead(sender, body)
            else -> {
                if (PrefsReader.deleteSmsEnabled(mPluginContext)) {
                    deleteSms(sender, body)
                } else if (PrefsReader.markAsReadEnabled(mPluginContext)) {
                    markSmsAsRead(sender, body)
                }
            }
        }
        return null
    }

    private fun markSmsAsRead(sender: String?, body: String?) {
        XLog.d("Marking SMS as read...")
        val report = operateSmsWithRetry(sender, body, OP_MARK_AS_READ)
        if (report.success) {
            XLog.i("Mark SMS as read succeed")
        } else {
            XLog.i("Mark SMS as read failed")
            logSmsOpFailureDetails("mark_as_read", report)
        }
    }

    private fun deleteSms(sender: String?, body: String?) {
        XLog.d("Deleting SMS...")
        val report = operateSmsWhenInserted(sender, body, OP_DELETE)
        if (report.success) {
            XLog.i("Delete SMS succeed")
        } else {
            XLog.i("Delete SMS failed")
            logSmsOpFailureDetails("delete", report)
        }
    }

    private fun operateSmsWithRetry(sender: String?, body: String?, @SmsOp smsOp: Int): SmsOpRetryReport {
        var accumulatedScanned = 0
        var maxMatched = 0
        var lastReport = SmsOpAttemptReport()

        repeat(SMS_OP_RETRY_TIMES) { attempt ->
            val report = operateSms(sender, body, smsOp)
            lastReport = report
            accumulatedScanned += report.scannedRows
            if (report.matchedRows > maxMatched) maxMatched = report.matchedRows
            if (report.success) {
                return SmsOpRetryReport(
                    success = true,
                    attempts = attempt + 1,
                    scannedRows = accumulatedScanned,
                    maxMatchedRows = maxMatched,
                    lastAttempt = report,
                )
            }
            XLog.d(
                "SMS op retry pending: op=%s attempt=%d/%d scanned=%d matched=%d uri=%s targetId=%s rows=%d cursorNull=%s permissionDenied=%s err=%s",
                smsOpName(smsOp),
                attempt + 1,
                SMS_OP_RETRY_TIMES,
                report.scannedRows,
                report.matchedRows,
                report.uri,
                report.targetId ?: "-",
                report.rowsAffected,
                report.cursorNull,
                report.permissionDenied,
                report.error?.message ?: "-",
            )
            if (attempt < SMS_OP_RETRY_TIMES - 1) {
                SystemClock.sleep(SMS_OP_RETRY_DELAY_MS)
            }
        }
        return SmsOpRetryReport(
            success = false,
            attempts = SMS_OP_RETRY_TIMES,
            scannedRows = accumulatedScanned,
            maxMatchedRows = maxMatched,
            lastAttempt = lastReport,
        )
    }

    /**
     * Event-driven path for delete: wait for sms provider change, then apply delete.
     * Avoids fixed-interval blind retries.
     */
    private fun operateSmsWhenInserted(sender: String?, body: String?, @SmsOp smsOp: Int): SmsOpRetryReport {
        val initial = operateSms(sender, body, smsOp)
        if (initial.success) {
            return SmsOpRetryReport(
                success = true,
                attempts = 1,
                scannedRows = initial.scannedRows,
                maxMatchedRows = initial.matchedRows,
                lastAttempt = initial,
            )
        }
        if (smsOp != OP_DELETE) {
            return SmsOpRetryReport(
                success = false,
                attempts = 1,
                scannedRows = initial.scannedRows,
                maxMatchedRows = initial.matchedRows,
                lastAttempt = initial,
            )
        }

        val latch = CountDownLatch(1)
        val changes = AtomicInteger(0)
        val scanned = AtomicInteger(initial.scannedRows)
        val maxMatched = AtomicInteger(initial.matchedRows)
        val lastAttempt = AtomicReference(initial)

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                changes.incrementAndGet()
                val report = operateSms(sender, body, smsOp)
                lastAttempt.set(report)
                scanned.addAndGet(report.scannedRows)
                updateMax(maxMatched, report.matchedRows)
                if (report.success) {
                    XLog.d(
                        "SMS op observer success: op=%s changeCount=%d uri=%s targetId=%s rows=%d",
                        smsOpName(smsOp),
                        changes.get(),
                        uri?.toString() ?: "-",
                        report.targetId ?: "-",
                        report.rowsAffected,
                    )
                    latch.countDown()
                } else {
                    XLog.d(
                        "SMS op observer pending: op=%s changeCount=%d scanned=%d matched=%d uri=%s targetId=%s rows=%d err=%s",
                        smsOpName(smsOp),
                        changes.get(),
                        report.scannedRows,
                        report.matchedRows,
                        report.uri,
                        report.targetId ?: "-",
                        report.rowsAffected,
                        report.error?.message ?: "-",
                    )
                }
            }
        }

        mPhoneContext.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        return try {
            val completed = latch.await(SMS_DELETE_OBSERVER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!completed) {
                XLog.d(
                    "SMS op observer timeout: op=%s changes=%d scanned=%d matched=%d",
                    smsOpName(smsOp),
                    changes.get(),
                    scanned.get(),
                    maxMatched.get(),
                )
                val finalReport = operateSms(sender, body, smsOp)
                lastAttempt.set(finalReport)
                scanned.addAndGet(finalReport.scannedRows)
                updateMax(maxMatched, finalReport.matchedRows)
            }
            val final = lastAttempt.get()
            SmsOpRetryReport(
                success = final.success,
                attempts = 1 + changes.get() + if (completed) 0 else 1,
                scannedRows = scanned.get(),
                maxMatchedRows = maxMatched.get(),
                lastAttempt = final,
            )
        } finally {
            runCatching {
                mPhoneContext.contentResolver.unregisterContentObserver(observer)
            }.onFailure {
                XLog.w("Failed to unregister SMS observer", it)
            }
        }
    }

    private fun operateSms(sender: String?, body: String?, @SmsOp smsOp: Int): SmsOpAttemptReport {
        var cursor: android.database.Cursor? = null
        var scannedRows = 0
        var matchedRows = 0
        var targetId: String? = null
        var rowsAffected = 0
        var lastError: Throwable? = null
        val mismatchSamples = mutableListOf<String>()
        val uriString = Telephony.Sms.CONTENT_URI.toString()
        try {
            if (ContextCompat.checkSelfPermission(mPhoneContext, Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                XLog.e("Don't have permission to read/write sms")
                return SmsOpAttemptReport(
                    success = false,
                    scannedRows = scannedRows,
                    matchedRows = matchedRows,
                    targetId = targetId,
                    rowsAffected = rowsAffected,
                    uri = uriString,
                    permissionDenied = true,
                )
            }
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.READ,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.DATE,
            )
            // 查看最近短信并匹配目标
            val minDate = (mSmsMsg.date - SMS_MATCH_WINDOW_MS).coerceAtLeast(0L)
            val selection = "${Telephony.Sms.DATE} >= ?"
            val selectionArgs = arrayOf(minDate.toString())
            val sortOrder = Telephony.Sms.DATE + " desc limit " + SMS_QUERY_ROW_LIMIT
            // Use phone context to keep telephony provider access in telephony process identity.
            val resolver = mPhoneContext.contentResolver
            val smsUri = Telephony.Sms.CONTENT_URI
            cursor = resolver.query(smsUri, projection, selection, selectionArgs, sortOrder)
            if (cursor == null) {
                XLog.d("Cursor is null")
                return SmsOpAttemptReport(
                    success = false,
                    scannedRows = scannedRows,
                    matchedRows = matchedRows,
                    targetId = targetId,
                    rowsAffected = rowsAffected,
                    uri = uriString,
                    cursorNull = true,
                )
            }
            while (cursor.moveToNext()) {
                scannedRows++
                val curAddress = cursor.getString(cursor.getColumnIndexOrThrow("address"))
                val curRead = cursor.getInt(cursor.getColumnIndexOrThrow("read"))
                val curBody = cursor.getString(cursor.getColumnIndexOrThrow("body"))
                val curDate = cursor.getLong(cursor.getColumnIndexOrThrow("date"))
                val senderMatched = isAddressMatched(curAddress, sender)
                val bodyMatched = isBodyMatched(curBody, body)
                if (!senderMatched || !bodyMatched) {
                    collectMismatchSample(
                        mismatchSamples = mismatchSamples,
                        curAddress = curAddress,
                        curBody = curBody,
                        curDate = curDate,
                        senderMatched = senderMatched,
                        bodyMatched = bodyMatched,
                    )
                    continue
                }
                matchedRows++
                if (smsOp == OP_MARK_AS_READ && curRead != 0) {
                    // Treat already-read as success to avoid false failure reporting.
                    return SmsOpAttemptReport(
                        success = true,
                        scannedRows = scannedRows,
                        matchedRows = matchedRows,
                        targetId = targetId,
                        rowsAffected = 0,
                        uri = uriString,
                    )
                }
                if (bodyMatched) {
                    val smsMessageId = cursor.getString(cursor.getColumnIndexOrThrow("_id"))
                    targetId = smsMessageId
                    val threadId = cursor.getLong(cursor.getColumnIndexOrThrow("thread_id"))
                    val where = Telephony.Sms._ID + " = ?"
                    val selectionArgs = arrayOf(smsMessageId)
                    if (smsOp == OP_DELETE) {
                        rowsAffected = resolver.delete(smsUri, where, selectionArgs)
                        if (rowsAffected > 0) {
                            notifyExternalProviderChange()
                            return SmsOpAttemptReport(
                                success = true,
                                scannedRows = scannedRows,
                                matchedRows = matchedRows,
                                targetId = targetId,
                                rowsAffected = rowsAffected,
                                uri = uriString,
                            )
                        }
                    } else if (smsOp == OP_MARK_AS_READ) {
                        val values = ContentValues()
                        values.put(Telephony.Sms.READ, true)
                        values.put(Telephony.Sms.SEEN, true)
                        rowsAffected = resolver.update(smsUri, values, where, selectionArgs)
                        if (rowsAffected > 0) {
                            markRelatedSmsAsReadInThread(threadId, sender)
                            notifyExternalProviderChange()
                            return SmsOpAttemptReport(
                                success = true,
                                scannedRows = scannedRows,
                                matchedRows = matchedRows,
                                targetId = targetId,
                                rowsAffected = rowsAffected,
                                uri = uriString,
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            lastError = e
            XLog.e("Operate SMS failed: ", e)
        } finally {
            cursor?.close()
        }
        return SmsOpAttemptReport(
            success = false,
            scannedRows = scannedRows,
            matchedRows = matchedRows,
            targetId = targetId,
            rowsAffected = rowsAffected,
            uri = uriString,
            sample = mismatchSamples.joinToString(" | ").ifBlank { null },
            error = lastError,
        )
    }

    private fun isAddressMatched(curAddress: String?, targetAddress: String?): Boolean {
        if (curAddress.isNullOrBlank() || targetAddress.isNullOrBlank()) return false
        if (curAddress == targetAddress) return true
        val normalizedCurrent = curAddress.filter { it.isDigit() }
        val normalizedTarget = targetAddress.filter { it.isDigit() }
        if (normalizedCurrent.isBlank() || normalizedTarget.isBlank()) {
            return false
        }
        return normalizedCurrent.endsWith(normalizedTarget) || normalizedTarget.endsWith(normalizedCurrent)
    }

    private fun markRelatedSmsAsReadInThread(threadId: Long, sender: String?) {
        if (threadId <= 0L || sender.isNullOrBlank()) return
        try {
            val where = (
                "${Telephony.Sms.THREAD_ID} = ? AND " +
                    "${Telephony.Sms.ADDRESS} = ? AND " +
                    "${Telephony.Sms.READ} = 0"
                )
            val selectionArgs = arrayOf(threadId.toString(), sender)
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, true)
                put(Telephony.Sms.SEEN, true)
            }
            val rows = mPluginContext.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                where,
                selectionArgs,
            )
            if (rows > 0) {
                XLog.d("Marked $rows unread SMS as read in same thread: $threadId")
            }
        } catch (e: Exception) {
            XLog.w("Failed to mark related SMS as read in thread: $threadId", e)
        }
    }

    private fun isBodyMatched(curBody: String?, targetBody: String?): Boolean {
        if (curBody.isNullOrEmpty() || targetBody.isNullOrEmpty()) return false
        if (curBody.startsWith(targetBody) || targetBody.startsWith(curBody)) return true

        val normalizedCur = curBody.filterNot { it.isWhitespace() }
        val normalizedTarget = targetBody.filterNot { it.isWhitespace() }
        if (normalizedCur.startsWith(normalizedTarget) || normalizedTarget.startsWith(normalizedCur)) {
            return true
        }

        val targetCode = mSmsMsg.smsCode?.filter { it.isDigit() }
        if (!targetCode.isNullOrBlank() && targetCode.length >= 4) {
            val curDigits = curBody.filter { it.isDigit() }
            val targetDigits = targetBody.filter { it.isDigit() }
            if (curDigits.contains(targetCode) && targetDigits.contains(targetCode)) {
                return true
            }
        }
        return false
    }

    private fun collectMismatchSample(
        mismatchSamples: MutableList<String>,
        curAddress: String?,
        curBody: String?,
        curDate: Long,
        senderMatched: Boolean,
        bodyMatched: Boolean,
    ) {
        if (mismatchSamples.size >= SMS_MISMATCH_SAMPLE_LIMIT) return
        val closeByDate = abs(curDate - mSmsMsg.date) <= SMS_MATCH_WINDOW_MS
        if (!senderMatched && !bodyMatched && !closeByDate) return

        val bodyPreview = (curBody ?: "").replace("\n", "\\n").take(36)
        mismatchSamples += buildString {
            append("addr=")
            append(curAddress ?: "<null>")
            append(",dateDeltaMs=")
            append(abs(curDate - mSmsMsg.date))
            append(",senderMatched=")
            append(senderMatched)
            append(",bodyMatched=")
            append(bodyMatched)
            append(",body=")
            append(bodyPreview)
        }
    }

    private fun notifyExternalProviderChange() {
        try {
            val packages = linkedSetOf<String>()
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(mPluginContext)
            if (!defaultSmsPackage.isNullOrBlank()) {
                packages.add(defaultSmsPackage)
            }
            packages.add(GOOGLE_MESSAGES_PACKAGE_NAME)

            sendExternalProviderChange(packages)
            Handler(Looper.getMainLooper()).postDelayed(
                { sendExternalProviderChange(packages) },
                EXTERNAL_PROVIDER_CHANGE_DELAY_MS,
            )
        } catch (e: Exception) {
            XLog.w("Failed to schedule external provider change broadcast", e)
        }
    }

    private fun sendExternalProviderChange(packages: Set<String>) {
        try {
            packages.forEach { packageName ->
                val intent = Intent(Telephony.Sms.Intents.ACTION_EXTERNAL_PROVIDER_CHANGE).apply {
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    setPackage(packageName)
                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                }
                mPhoneContext.sendBroadcast(intent)
                XLog.d("Sent external provider change broadcast to: $packageName")
            }
        } catch (e: Exception) {
            XLog.w("Failed to send external provider change broadcast", e)
        }
    }

    private fun smsOpName(@SmsOp smsOp: Int): String = if (smsOp == OP_DELETE) "delete" else "mark_as_read"

    private fun updateMax(target: AtomicInteger, value: Int) {
        while (true) {
            val current = target.get()
            if (value <= current) return
            if (target.compareAndSet(current, value)) return
        }
    }

    private fun logSmsOpFailureDetails(opName: String, report: SmsOpRetryReport) {
        val last = report.lastAttempt
        XLog.w(
            "SMS op failed detail: op=%s attempts=%d scanned=%d maxMatched=%d uri=%s targetId=%s rows=%d cursorNull=%s permissionDenied=%s sample=%s err=%s",
            opName,
            report.attempts,
            report.scannedRows,
            report.maxMatchedRows,
            last.uri,
            last.targetId ?: "-",
            last.rowsAffected,
            last.cursorNull,
            last.permissionDenied,
            last.sample ?: "-",
            last.error?.message ?: "-",
        )
        last.error?.let {
            XLog.w("SMS op failed stacktrace", it)
        }
    }

    private data class SmsOpAttemptReport(
        val success: Boolean = false,
        val scannedRows: Int = 0,
        val matchedRows: Int = 0,
        val targetId: String? = null,
        val rowsAffected: Int = 0,
        val uri: String = Telephony.Sms.CONTENT_URI.toString(),
        val cursorNull: Boolean = false,
        val permissionDenied: Boolean = false,
        val sample: String? = null,
        val error: Throwable? = null,
    )

    private data class SmsOpRetryReport(
        val success: Boolean,
        val attempts: Int,
        val scannedRows: Int,
        val maxMatchedRows: Int,
        val lastAttempt: SmsOpAttemptReport,
    )

    companion object {
        const val FORCE_DELETE = 0
        private const val OP_DELETE = FORCE_DELETE
        private const val OP_MARK_AS_READ = 1
        private const val GOOGLE_MESSAGES_PACKAGE_NAME = "com.google.android.apps.messaging"
        private const val EXTERNAL_PROVIDER_CHANGE_DELAY_MS = 500L
        private const val SMS_DELETE_OBSERVER_TIMEOUT_MS = 45000L
        private const val SMS_MATCH_WINDOW_MS = 5 * 60 * 1000L
        private const val SMS_QUERY_ROW_LIMIT = 200
        private const val SMS_MISMATCH_SAMPLE_LIMIT = 3
        private const val SMS_OP_RETRY_TIMES = 8
        private const val SMS_OP_RETRY_DELAY_MS = 1200L
    }
}
