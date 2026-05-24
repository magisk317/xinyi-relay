package io.github.magisk317.relay.xpbridge

import android.content.Context

object XpCodeRecordExporter {
    fun exportToFile(context: Context, smsMsg: SmsMsg): Boolean {
        val appContext = context.applicationContext ?: context
        return XpRecordFacade.activeRuntimeBridge.exportCodeRecordToFile(appContext, smsMsg.toRecord())
    }
}
