package com.github.magisk317.smscode.forwarder.recovery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RootDbCatchupEngineTest {

    @Test
    fun callTypeLabel_mapsKnownTypes() {
        assertEquals("来电", RootDbCatchupEngine.callTypeLabel(1))
        assertEquals("去电", RootDbCatchupEngine.callTypeLabel(2))
        assertEquals("未接", RootDbCatchupEngine.callTypeLabel(3))
        assertEquals("语音信箱", RootDbCatchupEngine.callTypeLabel(4))
        assertEquals("拒接", RootDbCatchupEngine.callTypeLabel(5))
        assertEquals("拦截", RootDbCatchupEngine.callTypeLabel(6))
        assertEquals("异地接听", RootDbCatchupEngine.callTypeLabel(7))
    }

    @Test
    fun callTypeLabel_mapsUnknownTypes() {
        assertEquals("未知类型(0)", RootDbCatchupEngine.callTypeLabel(0))
        assertEquals("未知类型(99)", RootDbCatchupEngine.callTypeLabel(99))
    }
}
