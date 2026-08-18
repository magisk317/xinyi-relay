package io.github.magisk317.relay.ui.record

/**
 * Enables the child row's anchored dismiss gesture only on the settled Records page. Non-row
 * surfaces do not install this handler, so their horizontal drags continue to the parent pager.
 */
internal fun recordRowDismissEnabled(
    isActive: Boolean,
    isSelectionMode: Boolean,
): Boolean = isActive && !isSelectionMode

/** Hidden retained pages must never register a higher-priority system-back callback. */
internal fun recordSelectionBackEnabled(
    isActive: Boolean,
    isSelectionMode: Boolean,
): Boolean = isActive && isSelectionMode
