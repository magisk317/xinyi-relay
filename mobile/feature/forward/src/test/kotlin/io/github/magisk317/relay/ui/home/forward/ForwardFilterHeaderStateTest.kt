package io.github.magisk317.relay.ui.home.forward

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ForwardFilterHeaderStateTest {

    @Test
    fun normalizeAppForwardFilterLabel_usesResolvedLabelWhenPresent() {
        assertEquals(
            "Bank",
            normalizeAppForwardFilterLabel(
                packageName = "com.example.bank",
                resolvedLabel = " Bank ",
            ),
        )
    }

    @Test
    fun normalizeAppForwardFilterLabel_fallsBackToPackageName() {
        assertEquals(
            "com.example.bank",
            normalizeAppForwardFilterLabel(
                packageName = " com.example.bank ",
                resolvedLabel = " ",
            ),
        )
    }
}
