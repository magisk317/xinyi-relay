package io.github.magisk317.relay.xpbridge

import android.content.Context
import io.github.magisk317.relay.domain.system.RuntimeCodeRecordFileStore

object XpCodeRecordExporter {
    fun exportToFile(context: Context, smsMsg: SmsMsg): Boolean {
        return RuntimeCodeRecordFileStore.exportToFile(context, smsMsg.toRuntime())
    }
}
