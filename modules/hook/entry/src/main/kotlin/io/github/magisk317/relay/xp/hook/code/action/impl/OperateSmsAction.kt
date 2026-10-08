package io.github.magisk317.relay.xp.hook.code.action.impl

import android.content.Context
import android.os.Bundle
import androidx.annotation.IntDef
import io.github.magisk317.relay.xp.hook.code.action.CallableAction
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.smscode.runtime.verification.OperateSmsActionHelper

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
        OperateSmsActionHelper(
            pluginContext = mPluginContext,
            phoneContext = mPhoneContext,
            smsMsg = mSmsMsg,
            deleteSmsEnabledReader = XpPrefs::deleteSmsEnabled,
            markAsReadEnabledReader = XpPrefs::markAsReadEnabled,
        ).execute(
            OperateSmsActionHelper.resolveForcedOperation(
                forcedOperation = forcedOp,
                deleteValue = OP_DELETE,
                markAsReadValue = OP_MARK_AS_READ,
            ),
        )
        return null
    }

    companion object {
        const val OP_DELETE = 0
        const val FORCE_DELETE = OP_DELETE
        const val OP_MARK_AS_READ = 1
    }
}
