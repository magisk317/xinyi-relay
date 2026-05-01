package io.github.magisk317.relay.prefs

import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AppPreferencesDataStoreRepairTest {

    @Test
    fun normalizeTypedPrefValue_booleanString_isConverted() {
        assertEquals(
            false,
            AppPreferencesDataStore.normalizeTypedPrefValue(PrefConst.KEY_ENABLE_ANALYTICS, "false"),
        )
        assertEquals(
            true,
            AppPreferencesDataStore.normalizeTypedPrefValue(PrefConst.KEY_RELAY_BY_WIFI, "1"),
        )
    }

    @Test
    fun normalizeTypedPrefValue_numberAndFloatStrings_areConverted() {
        assertEquals(
            25,
            AppPreferencesDataStore.normalizeTypedPrefValue(PrefConst.KEY_LOW_BATTERY_THRESHOLD, "25"),
        )
        assertEquals(
            0.5f,
            AppPreferencesDataStore.normalizeTypedPrefValue(PrefConst.KEY_HAZE_TINT_ALPHA, "0.5"),
        )
    }

    @Test
    fun normalizeTypedPrefValue_invalidBooleanString_returnsNull() {
        assertNull(
            AppPreferencesDataStore.normalizeTypedPrefValue(PrefConst.KEY_ENABLE_ANALYTICS, "not_bool"),
        )
    }
}
