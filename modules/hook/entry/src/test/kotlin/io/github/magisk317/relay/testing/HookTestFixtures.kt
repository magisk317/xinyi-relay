package io.github.magisk317.relay.testing

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.smscode.xposed.utils.XLog
import io.mockk.every
import io.mockk.mockk

data class HookTestContexts(
    val pluginContext: Context,
    val phoneContext: Context,
)

fun relaxedHookContexts(): HookTestContexts = HookTestContexts(
    pluginContext = mockk(relaxed = true),
    phoneContext = mockk(relaxed = true),
)

fun strictHookContexts(): HookTestContexts = HookTestContexts(
    pluginContext = mockk(),
    phoneContext = mockk(),
)

fun relaxedIntent(): Intent = mockk(relaxed = true)

fun hookSmsMsg(
    sender: String = "1068",
    body: String = "otp 123456",
    date: Long = 100L,
    smsCode: String? = null,
    packageName: String? = null,
): SmsMsg = SmsMsg(
    sender = sender,
    body = body,
    date = date,
    msgType = SmsMsg.MSG_TYPE_SMS,
    smsCode = smsCode,
    packageName = packageName,
)

fun installSilentXpLogSink() {
    XLog.setTestSink { _, _ -> }
}

fun clearXpLogSink() {
    XLog.setTestSink(null)
}

fun Intent.stubHookSimRouting(
    simSlotKey: String = "sim_slot",
    simSlot: Int? = null,
    subIdKey: String = "sub_id",
    subId: Int? = null,
) {
    hookSimSlotKeys.forEach { key -> every { hasExtra(key) } returns false }
    hookSubIdKeys.forEach { key -> every { hasExtra(key) } returns false }
    simSlot?.let { value ->
        every { hasExtra(simSlotKey) } returns true
        every { getIntExtra(simSlotKey, Int.MIN_VALUE) } returns value
    }
    subId?.let { value ->
        every { hasExtra(subIdKey) } returns true
        every { getIntExtra(subIdKey, Int.MIN_VALUE) } returns value
    }
}

private val hookSimSlotKeys = listOf(
    "sim_slot",
    "slot",
    "simId",
    "sim_id",
    "simSlot",
    "android.telephony.extra.SLOT_INDEX",
)

private val hookSubIdKeys = listOf(
    "sub_id",
    "subscription",
    "subscription_id",
    "android.telephony.extra.SUBSCRIPTION_INDEX",
    "android.telephony.extra.SUBSCRIPTION_ID",
)
