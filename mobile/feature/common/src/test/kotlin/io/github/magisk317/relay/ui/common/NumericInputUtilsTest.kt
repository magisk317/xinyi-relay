package io.github.magisk317.relay.ui.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NumericInputUtilsTest {
    @Test
    fun `filter keeps digits only`() {
        assertEquals("123", filterNonNegativeIntegerInput("a1b2c3"))
        assertEquals("", filterNonNegativeIntegerInput("abc"))
    }

    @Test
    fun `normalize drops whitespace and format characters but keeps other content`() {
        assertEquals("12x3", normalizeIntegerInput(" 1 2x3"))
        assertEquals("12", normalizeIntegerInput("1\u200b2"))
    }

    @Test
    fun `non-negative long parse rejects negatives and garbage`() {
        assertEquals(42L, parseNonNegativeLongInput("42"))
        assertNull(parseNonNegativeLongInput("-1"))
        assertNull(parseNonNegativeLongInput("abc"))
    }

    @Test
    fun `parse at least rejects values below the floor`() {
        assertEquals(5, parseIntAtLeastInput("5", min = 1))
        assertNull(parseIntAtLeastInput("0", min = 1))
        assertEquals(3, parseIntAtLeastInput("03", min = 1))
    }
}
