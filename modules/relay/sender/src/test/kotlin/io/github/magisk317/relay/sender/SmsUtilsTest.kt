package io.github.magisk317.relay.sender

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SmsUtilsTest {
    @Test
    fun normalizeTargetMobiles_splitsCommonSeparatorsAndDeduplicates() {
        assertEquals(
            listOf("10086", "10010", "10001"),
            SmsUtils.normalizeTargetMobiles(" 10086，10010;10086；10001 ", sourceNumber = "1069"),
        )
    }

    @Test
    fun normalizeTargetMobiles_replacesSourcePlaceholders() {
        assertEquals(
            listOf("10690000", "10086"),
            SmsUtils.normalizeTargetMobiles("[from],{{来源号码}},10086", sourceNumber = "10690000"),
        )
    }
}
