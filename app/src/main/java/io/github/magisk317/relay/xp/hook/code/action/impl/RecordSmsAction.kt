package io.github.magisk317.relay.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import io.github.magisk317.relay.xpbridge.XpRecordFacade
import io.github.magisk317.relay.xpbridge.XpCodeRecordExporter
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpSharedRuntimeGate
import io.github.magisk317.relay.xp.hook.code.action.CallableAction
import io.github.magisk317.smscode.verification.RecordSmsDedupHelper
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
            withFileLock = { context, fileName, block ->
                XpSharedRuntimeGate.withFileLock(context, fileName) { block() }
            },
            shouldSkipByDedup = ::shouldSkipByDedup,
            primaryInserter = ::insertPrimary,
            fallbackExporter = ::exportFallback,
        ).run()
    }

    private fun insertPrimary(smsMsg: SmsMsg): RecordSmsActionHelper.InsertResult {
        return RecordSmsInsertResultHelper.capture {
            val recordId = runBlocking {
                runtimeRecordFacade.insertSmsRecord(
                    smsMsg = smsMsg,
                    isCodeSms = true,
                )
            }
            if (recordId != null) {
                return RecordSmsInsertResultHelper.success(detail = "record_id=$recordId")
            }
            RecordSmsInsertResultHelper.failure("insert_returned_null")
        }
    }

    private fun exportFallback(smsMsg: SmsMsg): Boolean {
        return XpCodeRecordExporter.exportToFile(mPluginContext, smsMsg)
    }

    private fun shouldSkipByDedup(smsMsg: SmsMsg, eventLabel: String): Boolean {
        return RecordSmsDedupHelper.shouldSkipByWindow(
            smsMsg = smsMsg,
            eventLabel = eventLabel,
            hasFingerprintDuplicate = { sender, body, from, to ->
                runBlocking {
                    runCatching {
                        runtimeRecordFacade.hasSmsDuplicateInRange(
                            sender = sender,
                            body = body,
                            dateFrom = from,
                            dateTo = to,
                            msgType = SmsMsg.MSG_TYPE_SMS,
                        )
                    }.getOrDefault(false)
                }
            },
            hasCodeDuplicateByPackage = { code, pkg, from, to ->
                runBlocking {
                    runCatching {
                        runtimeRecordFacade.hasSmsCodeDuplicateByPackageInRange(code, pkg, from, to)
                    }.getOrDefault(false)
                }
            },
            hasCodeDuplicateByCompany = { code, company, from, to ->
                runBlocking {
                    runCatching {
                        runtimeRecordFacade.hasSmsCodeDuplicateByCompanyInRange(code, company, from, to)
                    }.getOrDefault(false)
                }
            },
        )
    }
}
