package io.github.magisk317.relay.domain.sender

import io.github.magisk317.relay.engine.sender.SenderType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SenderTypeDisplayNameTest {

    @Test
    fun displayName_usesConfiguredNameWithoutTypePrefix() {
        assertEquals("主力飞书", SenderType.displayName(SenderType.FEISHU, " 主力飞书 "))
    }

    @Test
    fun displayName_usesTypeNameWhenConfiguredNameIsMissingOrGeneratedFallback() {
        assertEquals("飞书应用", SenderType.displayName(SenderType.FEISHU_APP, ""))
        assertEquals("飞书应用", SenderType.displayName(SenderType.FEISHU_APP, "13"))
        assertEquals("飞书应用", SenderType.displayName(SenderType.FEISHU_APP, "通道13"))
        assertEquals("Matrix", SenderType.displayName(SenderType.MATRIX, "19"))
    }
}
