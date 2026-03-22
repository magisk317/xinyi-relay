package io.github.magisk317.relay.xp.hook.code.action.impl

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.xp.SmsMsg
import io.github.magisk317.relay.xp.XpDispatchCoordinator
import io.github.magisk317.relay.xp.XpMessageTypes
import io.github.magisk317.relay.xp.XpRecordFacade
import io.github.magisk317.relay.xp.XpStringEscaper
import io.github.magisk317.relay.xp.hook.code.action.CallableAction
import io.github.magisk317.smscode.core.utils.XLog

/**
 * 解析短信中的验证码
 */
class SmsParseAction(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg?) :
    CallableAction(pluginContext, phoneContext, smsMsg ?: SmsMsg()) {

    private var mSmsIntent: Intent? = null
    private var mDeduplicateEnabled: Boolean = false
    private val runtimeRecordFacade = XpRecordFacade(pluginContext)

    fun setSmsIntent(smsIntent: Intent?) {
        mSmsIntent = smsIntent
    }

    fun setDeduplicateEnabled(enabled: Boolean) {
        mDeduplicateEnabled = enabled
    }

    override fun action(): Bundle? = parseSmsMsg()

    private fun parseSmsMsg(): Bundle? {
        val intent = mSmsIntent ?: return null
        val smsMsg = SmsMsg.fromIntent(intent)
        XLog.w(
            "Diag SMS parsed from intent: senderPresent=%s, bodyLength=%d, timestamp=%d",
            !smsMsg.sender.isNullOrBlank(),
            smsMsg.body?.length ?: 0,
            smsMsg.date,
        )
        // Update the member variable of super class if possible, but it's val.
        // Actually, CallableAction should have var mSmsMsg or we use the local one.
        // Wait, CallableAction has @JvmField protected val mSmsMsg.
        // I'll use a local variable and update fields of mSmsMsg if it's not final in Java.
        // But in Kotlin it's val.

        val sender = smsMsg.sender
        val msgBody = smsMsg.body

        if (BuildConfig.DEBUG) {
            XLog.d("Sender: %s", sender)
            XLog.d("Body: %s", msgBody)
        } else {
            XLog.d("Sender: %s", XpStringEscaper.escape(sender))
            XLog.d("Body length: %d", msgBody?.length ?: 0)
        }

        if (TextUtils.isEmpty(sender) || TextUtils.isEmpty(msgBody)) {
            XLog.w("Diag SMS parse aborted: sender/body is empty")
            return null
        }

        val msgBodyNotNull = msgBody ?: ""
        val timestamp = if (smsMsg.date > 0) smsMsg.date else System.currentTimeMillis()
        XLog.w("Diag SMS body: %s", XpStringEscaper.escape(msgBodyNotNull))
        if (mDeduplicateEnabled) {
            val duplicated = kotlinx.coroutines.runBlocking {
                runCatching {
                    runtimeRecordFacade.isDuplicateSms(
                        sender = sender,
                        body = msgBodyNotNull,
                        date = timestamp,
                        msgType = SmsMsg.MSG_TYPE_SMS,
                    )
                }.getOrDefault(false)
            }
            if (duplicated) {
                XLog.i("Duplicate SMS detected by fingerprint, skip parsing.")
                return Bundle().apply { putBoolean(SMS_DUPLICATED, true) }
            }
        }

        val prepared = kotlinx.coroutines.runBlocking {
            XpDispatchCoordinator.prepareIngressSms(
                pluginContext = mPluginContext,
                phoneContext = mPhoneContext,
                smsMsg = smsMsg,
                sourceIntent = intent,
            )
        } ?: return null
        if (!XpMessageTypes.isSmsCode(prepared.messageType)) {
            XLog.w("Diag SMS parsed but no code matched, body=%s", XpStringEscaper.escape(msgBodyNotNull))
            return null
        }
        val resolvedSmsMsg = prepared.smsMsg
        val smsCode = resolvedSmsMsg.smsCode.orEmpty()

        val company = resolvedSmsMsg.company.orEmpty()
        mSmsMsg = resolvedSmsMsg.copy(date = timestamp)
        XLog.w(
            "Diag SMS code matched: companyPresent=%s, codeLength=%d, code=%s, body=%s",
            !company.isNullOrBlank(),
            smsCode.length,
            XpStringEscaper.escape(smsCode),
            XpStringEscaper.escape(msgBodyNotNull),
        )

        val bundle = Bundle()
        bundle.putParcelable(SMS_MSG, mSmsMsg)

        bundle.putBoolean(SMS_DUPLICATED, false)
        return bundle
    }

    companion object {
        const val SMS_MSG = "sms_msg"
        const val SMS_DUPLICATED = "sms_duplicated"
    }
}
