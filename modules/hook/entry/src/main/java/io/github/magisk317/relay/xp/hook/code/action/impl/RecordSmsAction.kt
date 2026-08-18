package io.github.magisk317.relay.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import io.github.magisk317.relay.xpbridge.XpRecordFacade
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xp.hook.code.action.CallableAction
import io.github.magisk317.smscode.verification.RecordSmsActionHelper
import io.github.magisk317.smscode.verification.RecordSmsInsertResultHelper
import kotlinx.coroutines.runBlocking

/**
 * 记录验证码短信
 */
class RecordSmsAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    private val eventId: String = "",
    private val enabled: Boolean,
    private val deduplicateEnabled: Boolean,
) :
    CallableAction(pluginContext, phoneContext, smsMsg) {
    private val runtimeRecordFacade = XpRecordFacade(pluginContext)

    override fun action(): Bundle? {
        return RecordSmsActionHelper(
            pluginContext = mPluginContext,
            smsMsg = mSmsMsg,
            eventId = eventId,
            enabled = enabled,
            deduplicateEnabled = deduplicateEnabled,
            // The provider performs the fingerprint merge atomically in the app process.
            withFileLock = { _, _, block -> block() },
            // Do not query Room from a foreign hook UID before the provider call. The app-owned
            // provider applies the same window rules inside one database transaction.
            shouldSkipByDedup = { _, _ -> false },
            primaryInserter = ::insertPrimary,
            fallbackExporter = { false },
        ).run()
    }

    private fun insertPrimary(smsMsg: SmsMsg): RecordSmsActionHelper.InsertResult {
        return RecordSmsInsertResultHelper.capture {
            val recordId = runBlocking {
                runtimeRecordFacade.insertSmsRecord(
                    smsMsg = smsMsg,
                    isCodeSms = true,
                    deduplicate = deduplicateEnabled,
                )
            }
            if (recordId != null) {
                return RecordSmsInsertResultHelper.success(detail = "record_id=$recordId")
            }
            RecordSmsInsertResultHelper.failure("insert_returned_null")
        }
    }
}
