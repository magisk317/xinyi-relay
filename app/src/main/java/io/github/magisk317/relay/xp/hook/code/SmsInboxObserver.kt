package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.app.role.RoleManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.os.Build
import io.github.magisk317.relay.common.utils.PrefsReader
import io.github.magisk317.relay.common.utils.SmsCodeUtils
import io.github.magisk317.relay.common.utils.StringUtils
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.domain.system.RuntimeRecordFacade
import io.github.magisk317.relay.platform.ipc.SmsHookDispatchCoordinator
import io.github.magisk317.relay.xp.helper.ModuleConflictArbiter
import io.github.magisk317.smscode.core.utils.XLog
import java.util.Collections
import java.util.LinkedHashSet
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking

internal class SmsInboxObserver(
    private val pluginContext: Context,
    private val phoneContext: Context,
) {
    private val runtimeRecordFacade = RuntimeRecordFacade(pluginContext)
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            onChange(selfChange, null)
        }

        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            queryExecutor.execute { scanRecentInbox(uri?.toString().orEmpty()) }
        }
    }

    fun register() {
        runCatching {
            phoneContext.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
            XLog.i("SmsInboxObserver registered")
        }.onFailure {
            XLog.w("SmsInboxObserver register failed: %s", it.message ?: it.javaClass.simpleName)
        }
    }

    private fun scanRecentInbox(triggerUri: String) {
        val cutoff = System.currentTimeMillis() - RECENT_SMS_WINDOW_MS
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
        )
        val selection = "${Telephony.Sms.TYPE}=? AND ${Telephony.Sms.DATE}>?"
        val selectionArgs = arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString(), cutoff.toString())
        val sortOrder = "${Telephony.Sms.DATE} DESC limit $MAX_RECENT_SMS_COUNT"
        runCatching {
            phoneContext.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val smsId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))
                    if (!markSeen(smsId)) continue
                    val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)).orEmpty()
                    val code = runBlocking { SmsCodeUtils.parseSmsCodeIfExists(pluginContext, body) }.orEmpty()
                    if (code.isBlank()) continue
                    val sender = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)).orEmpty()
                    val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                    val read = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) != 0
                    XLog.w(
                        "Diag SMS provider observed: sms_id=%d trigger_uri=%s sender_hash=%s date=%d read=%s code=%s body=%s",
                        smsId,
                        triggerUri.ifBlank { Telephony.Sms.CONTENT_URI.toString() },
                        senderHash(sender),
                        date,
                        read,
                        StringUtils.escape(code),
                        StringUtils.escape(body),
                    )
                    logSmsRoleStateForSms(smsId, triggerUri)
                    handleObservedCode(
                        smsId = smsId,
                        triggerUri = triggerUri.ifBlank { Telephony.Sms.CONTENT_URI.toString() },
                        sender = sender,
                        body = body,
                        date = date,
                        read = read,
                        code = code,
                    )
                }
            }
        }.onFailure {
            XLog.w("SmsInboxObserver scan failed: %s", it.message ?: it.javaClass.simpleName)
        }
    }

    private fun handleObservedCode(
        smsId: Long,
        triggerUri: String,
        sender: String,
        body: String,
        date: Long,
        read: Boolean,
        code: String,
    ) {
        val eventId = buildObservedEventId(smsId, date)
        if (ModuleConflictArbiter.shouldSuppressByRelay(phoneContext, "SmsInboxObserver#handleObservedCode")) {
            XLog.w("Diag observer conflict skip: event_id=%s sms_id=%d", eventId, smsId)
            return
        }
        if (!PrefsReader.isEnabled(pluginContext)) {
            XLog.w("Diag observer skip: module disabled event_id=%s", eventId)
            return
        }
        logSmsRoleState(eventId)
        if (PrefsReader.deduplicateSms(pluginContext)) {
            val timestamp = if (date > 0) date else System.currentTimeMillis()
            val duplicated = runBlocking {
                runCatching {
                    runtimeRecordFacade.isDuplicateSms(
                        sender = sender,
                        body = body,
                        date = timestamp,
                        msgType = SmsMsg.MSG_TYPE_SMS,
                    )
                }.getOrDefault(false)
            }
            if (duplicated) {
                XLog.w("Diag observer duplicate skip: event_id=%s", eventId)
                return
            }
        }

        val smsMsg = SmsHookDispatchCoordinator.enrichObservedSms(
            phoneContext = phoneContext,
            sender = sender,
            body = body,
            date = date,
            smsCode = code,
        )

        if (PrefsReader.autoInputCodeEnabled(pluginContext)) {
            XLog.w(
                "Diag observer auto-input: event_id=%s sender_hash=%s read=%s uri=%s",
                eventId,
                senderHash(sender),
                read,
                triggerUri,
            )
            SmsCodePostParseCoordinator.runAutoInputNow(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
            )
        } else {
            XLog.w("Diag observer auto-input disabled: event_id=%s", eventId)
        }

        if (PrefsReader.deduplicateSms(pluginContext)) {
            XLog.w("Diag observer record skipped: dedup enabled event_id=%s", eventId)
            return
        }
        // Keep record behavior consistent with regular flow when enabled.
        SmsCodePostParseCoordinator.runRecordNow(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            eventId = eventId,
        )
    }

    private fun logSmsRoleState(eventId: String) {
        val (defaultSms, roleHolders) = resolveSmsRoleState()
        XLog.w(
            "Diag observer sms role: event_id=%s defaultSms=%s roleHolders=%s",
            eventId,
            defaultSms ?: "<none>",
            if (roleHolders.isEmpty()) "<none>" else roleHolders.joinToString(","),
        )
    }

    private fun logSmsRoleStateForSms(smsId: Long, triggerUri: String) {
        val (defaultSms, roleHolders) = resolveSmsRoleState()
        XLog.w(
            "Diag observer sms role: sms_id=%d trigger_uri=%s defaultSms=%s roleHolders=%s",
            smsId,
            triggerUri.ifBlank { Telephony.Sms.CONTENT_URI.toString() },
            defaultSms ?: "<none>",
            if (roleHolders.isEmpty()) "<none>" else roleHolders.joinToString(","),
        )
    }

    private fun resolveSmsRoleState(): Pair<String?, List<String>> {
        val defaultSms = runCatching { Telephony.Sms.getDefaultSmsPackage(phoneContext) }.getOrNull()
        val roleHolders: List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val roleManager = phoneContext.getSystemService(RoleManager::class.java)
                if (roleManager == null) {
                    emptyList()
                } else {
                    val method = roleManager.javaClass.getMethod("getRoleHolders", String::class.java)
                    @Suppress("UNCHECKED_CAST")
                    (method.invoke(roleManager, RoleManager.ROLE_SMS) as? List<*>)?.filterIsInstance<String>()
                        .orEmpty()
                }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        return defaultSms to roleHolders
    }

    private fun buildObservedEventId(smsId: Long, date: Long): String {
        val ts = if (date > 0) date else System.currentTimeMillis()
        return "sms_observed_${ts.toString(EVENT_ID_RADIX)}_${smsId.toString(EVENT_ID_RADIX)}"
    }

    private fun markSeen(smsId: Long): Boolean = synchronized(recentSmsIds) {
        if (!recentSmsIds.add(smsId)) {
            return false
        }
        while (recentSmsIds.size > MAX_TRACKED_SMS_IDS) {
            val first = recentSmsIds.firstOrNull() ?: break
            recentSmsIds.remove(first)
        }
        true
    }

    private fun senderHash(sender: String): String {
        if (sender.isBlank()) return "none"
        return Integer.toHexString(sender.hashCode())
    }

    companion object {
        private const val RECENT_SMS_WINDOW_MS = 10 * 60 * 1000L
        private const val MAX_RECENT_SMS_COUNT = 32
        private const val MAX_TRACKED_SMS_IDS = 128
        private const val EVENT_ID_RADIX = 36
        private val queryExecutor = Executors.newSingleThreadExecutor()
        private val recentSmsIds = Collections.synchronizedSet(LinkedHashSet<Long>())
    }
}
