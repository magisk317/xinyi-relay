package io.github.magisk317.relay.testing

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.android.data.db.dao.SmsMsgDao
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import io.github.magisk317.relay.platform.ipc.ForwardBroadcastContract
import io.mockk.every
import io.mockk.mockk

data class RuntimeDatabaseFixture(
    val database: AppDatabase,
    val smsMsgDao: SmsMsgDao,
)

fun relaxedContext(): Context = mockk(relaxed = true)

fun strictContext(): Context = mockk()

fun relaxedIntent(): Intent = mockk(relaxed = true)

fun runtimeSmsMsg(
    id: Long = 0L,
    sender: String = "1068",
    body: String = "code 123456",
    date: Long = 100L,
    company: String? = "Bank",
    smsCode: String? = "123456",
    packageName: String? = "com.bank.app",
    msgType: Int = SmsMsg.MSG_TYPE_SMS,
): SmsMsg = SmsMsg(
    id = id,
    sender = sender,
    body = body,
    date = date,
    company = company,
    smsCode = smsCode,
    packageName = packageName,
    msgType = msgType,
)

fun smsMsgDatabaseFixture(): RuntimeDatabaseFixture {
    val database = mockk<AppDatabase>(relaxed = true)
    val smsMsgDao = mockk<SmsMsgDao>(relaxed = true)
    every { database.smsMsgDao() } returns smsMsgDao
    return RuntimeDatabaseFixture(database = database, smsMsgDao = smsMsgDao)
}

fun Intent.stubStringExtra(
    key: String,
    value: String?,
) {
    every { getStringExtra(key) } returns value
}

fun Intent.stubLongExtra(
    key: String,
    value: Long,
    defaultValue: Long = 0L,
) {
    every { getLongExtra(key, defaultValue) } returns value
}

fun Intent.stubLongArrayExtra(
    key: String,
    value: LongArray?,
) {
    every { getLongArrayExtra(key) } returns value
}

fun Intent.stubSimRouting(
    simSlotKey: String = ForwardBroadcastContract.EXTRA_SIM_SLOT,
    simSlot: Int? = null,
    subIdKey: String = ForwardBroadcastContract.EXTRA_SUB_ID,
    subId: Int? = null,
) {
    runtimeSimSlotKeys.forEach { key -> every { hasExtra(key) } returns false }
    runtimeSubIdKeys.forEach { key -> every { hasExtra(key) } returns false }
    simSlot?.let { value ->
        every { hasExtra(simSlotKey) } returns true
        every { getIntExtra(simSlotKey, Int.MIN_VALUE) } returns value
    }
    subId?.let { value ->
        every { hasExtra(subIdKey) } returns true
        every { getIntExtra(subIdKey, Int.MIN_VALUE) } returns value
    }
}

private val runtimeSimSlotKeys = listOf(
    ForwardBroadcastContract.EXTRA_SIM_SLOT,
    "slot",
    "simId",
    "sim_id",
    "simSlot",
    "android.telephony.extra.SLOT_INDEX",
)

private val runtimeSubIdKeys = listOf(
    ForwardBroadcastContract.EXTRA_SUB_ID,
    "subscription",
    "subscription_id",
    "android.telephony.extra.SUBSCRIPTION_INDEX",
    "android.telephony.extra.SUBSCRIPTION_ID",
)
