package io.github.magisk317.relay.ui.record

import io.github.magisk317.relay.android.data.db.entity.SmsMsg

class RecordItem(val smsMsg: SmsMsg) : io.github.magisk317.uikit.shell.RecordItem<SmsMsg>(smsMsg)
