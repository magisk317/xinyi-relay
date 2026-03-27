package io.github.magisk317.relay.xp.hook.code.action.impl

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpDispatchCoordinator
import io.github.magisk317.relay.xpbridge.XpMessageTypes
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.relay.xpbridge.XpRecordFacade
import io.github.magisk317.relay.xpbridge.XpStringEscaper
import io.github.magisk317.relay.xp.hook.code.action.CallableAction
import io.github.magisk317.smscode.verification.SmsParseAction as SharedSmsParseAction
import io.github.magisk317.smscode.xposed.utils.XLog

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

    override fun action(): Bundle? {
        val outcome = SharedSmsParseAction(
            pluginContext = mPluginContext,
            phoneContext = mPhoneContext,
            smsIntent = mSmsIntent,
            deduplicateEnabled = mDeduplicateEnabled,
            incomingSmsParser = SmsMsg::fromIntent,
            sensitiveDebugLogReader = XpPrefs::isSensitiveDebugLogMode,
            summarizeSender = { sensitive, value ->
                if (sensitive) XpStringEscaper.escape(value).orEmpty() else XpStringEscaper.summarizeSender(value)
            },
            summarizeBody = { sensitive, value ->
                if (sensitive) XpStringEscaper.escape(value).orEmpty() else XpStringEscaper.summarizeBody(value)
            },
            summarizeCode = { sensitive, value ->
                if (sensitive) XpStringEscaper.escape(value).orEmpty() else XpStringEscaper.summarizeCode(value)
            },
            duplicateChecker = { sender, body, timestamp ->
                runCatching {
                    runtimeRecordFacade.isDuplicateSms(
                        sender = sender,
                        body = body,
                        date = timestamp,
                        msgType = SmsMsg.MSG_TYPE_SMS,
                    )
                }.getOrDefault(false)
            },
            preparedSmsResolver = { pluginContext, phoneContext, smsMsg, intent, timestamp ->
                val prepared = XpDispatchCoordinator.prepareIngressSms(
                    pluginContext = pluginContext,
                    phoneContext = phoneContext,
                    smsMsg = smsMsg,
                    sourceIntent = intent,
                ) ?: return@SharedSmsParseAction null
                if (!XpMessageTypes.isSmsCode(prepared.messageType)) {
                    return@SharedSmsParseAction null
                }
                prepared.smsMsg.copy(date = timestamp)
            },
        ).parse() ?: return null
        mSmsMsg = outcome.smsMsg ?: mSmsMsg
        return outcome.toBundle()
    }

    companion object {
        const val SMS_MSG = SharedSmsParseAction.KEY_SMS_MSG
        const val SMS_DUPLICATED = SharedSmsParseAction.KEY_SMS_DUPLICATED
    }
}
