package io.github.magisk317.relay.ui.record

import io.github.magisk317.smscode.db.entity.SmsMsg

class RecordItem(val smsMsg: SmsMsg) : io.github.magisk317.uikit.shell.RecordItem<SmsMsg>(smsMsg)
