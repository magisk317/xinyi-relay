package io.github.magisk317.relay.ui.record

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecordPagerGesturePolicyTest {
    @Test
    fun activeRecordRow_ownsHorizontalDismiss() {
        assertTrue(recordRowDismissEnabled(true, false))
    }

    @Test
    fun inactiveOrSelectionMode_neverEnablesRowDismiss() {
        assertFalse(recordRowDismissEnabled(false, false))
        assertFalse(recordRowDismissEnabled(true, true))
    }

    @Test
    fun hiddenRecordPage_neverOwnsSelectionBack() {
        assertTrue(recordSelectionBackEnabled(isActive = true, isSelectionMode = true))
        assertFalse(recordSelectionBackEnabled(isActive = false, isSelectionMode = true))
        assertFalse(recordSelectionBackEnabled(isActive = true, isSelectionMode = false))
    }
}
