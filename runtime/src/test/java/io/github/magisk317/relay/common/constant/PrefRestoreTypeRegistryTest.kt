package io.github.magisk317.relay.contract.constant

import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PrefRestoreTypeRegistryTest {

    @Test
    fun typeOf_recordSplitKeys_areBoolean() {
        assertEquals(PrefValueType.BOOLEAN, PrefRestoreTypeRegistry.typeOf(PrefConst.KEY_ENABLE_CODE_RECORDS_CODE))
        assertEquals(PrefValueType.BOOLEAN, PrefRestoreTypeRegistry.typeOf(PrefConst.KEY_ENABLE_CODE_RECORDS_PLAIN_SMS))
        assertEquals(PrefValueType.BOOLEAN, PrefRestoreTypeRegistry.typeOf(PrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY))
    }

    @Test
    fun typeOf_previousBooleanKeys_areBoolean() {
        assertEquals(PrefValueType.BOOLEAN, PrefRestoreTypeRegistry.typeOf(PrefConst.KEY_HIDE_LAUNCHER_ICON))
        assertEquals(PrefValueType.BOOLEAN, PrefRestoreTypeRegistry.typeOf(PrefConst.KEY_ENABLE_SMS_BLOCK))
        assertEquals(PrefValueType.BOOLEAN, PrefRestoreTypeRegistry.typeOf(PrefConst.KEY_ENABLE_NOTIFICATION_FORWARD))
    }

    @Test
    fun typeOf_restoreAndRelayBooleanKeys_areBoolean() {
        assertEquals(PrefValueType.BOOLEAN, PrefRestoreTypeRegistry.typeOf(PrefConst.KEY_ENABLE_ANALYTICS))
        assertEquals(PrefValueType.BOOLEAN, PrefRestoreTypeRegistry.typeOf(PrefConst.KEY_ENABLE_CALL_RELAY))
        assertEquals(PrefValueType.BOOLEAN, PrefRestoreTypeRegistry.typeOf(PrefConst.KEY_ENABLE_SMS_RELAY))
        assertEquals(PrefValueType.BOOLEAN, PrefRestoreTypeRegistry.typeOf(PrefConst.KEY_ENABLE_APP_RELAY))
        assertEquals(PrefValueType.BOOLEAN, PrefRestoreTypeRegistry.typeOf(PrefConst.KEY_RELAY_BY_WIFI))
        assertEquals(PrefValueType.BOOLEAN, PrefRestoreTypeRegistry.typeOf(PrefConst.KEY_RELAY_BY_DATA))
        assertEquals(PrefValueType.BOOLEAN, PrefRestoreTypeRegistry.typeOf(PrefConst.KEY_RELAY_KEYWORDS_CASE_INSENSITIVE))
    }

    @Test
    fun typeOf_runtimeLogSize_isInt() {
        assertEquals(PrefValueType.INT, PrefRestoreTypeRegistry.typeOf(PrefConst.KEY_RUNTIME_LOG_FILE_SIZE_MB))
    }

    @Test
    fun typeOf_dispatchStrategy_isInt() {
        assertEquals(PrefValueType.INT, PrefRestoreTypeRegistry.typeOf(PrefConst.KEY_FORWARD_COMMON_DISPATCH_STRATEGY))
    }

    @Test
    fun typeOf_unknownKey_defaultsToString() {
        assertEquals(PrefValueType.STRING, PrefRestoreTypeRegistry.typeOf("unknown_pref_key"))
    }
}
