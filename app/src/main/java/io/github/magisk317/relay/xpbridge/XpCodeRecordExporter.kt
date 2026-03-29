package io.github.magisk317.relay.xpbridge

import android.content.Context
import io.github.magisk317.relay.ui.record.CodeRecordRestoreManager

object XpCodeRecordExporter {
    fun exportToFile(context: Context, smsMsg: SmsMsg): Boolean {
        return CodeRecordRestoreManager.exportToFile(context, smsMsg)
    }
}
