package io.github.magisk317.relay.ui.record

import android.content.Context
import io.github.magisk317.forward.engine.model.ReadRecordData
import io.github.magisk317.relay.mobile.core.R
import io.github.magisk317.uikit.common.AppSnackbarDuration
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.common.AppSnackbarResult
import io.github.magisk317.uikit.common.showLatestSnackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Flips one record id inside the selection set. */
internal fun toggleSelectionId(selected: Set<Long>, id: Long): Set<Long> =
    if (selected.contains(id)) selected - id else selected + id

/**
 * Removes the given records and offers the undo snackbar; restoring re-inserts the
 * exact same records. Shared by row swipe, selection delete, and clear-tab.
 */
internal fun deleteRecordsWithUndo(
    viewModel: CodeRecordViewModel,
    scope: CoroutineScope,
    context: Context,
    snackbarHostState: AppSnackbarHostState,
    targets: List<ReadRecordData>,
) {
    if (targets.isEmpty()) return
    viewModel.removeSmsMsg(targets)
    scope.launch {
        val result = snackbarHostState.showLatestSnackbar(
            message = context.getString(R.string.some_items_removed, targets.size),
            actionLabel = context.getString(R.string.revoke),
            duration = AppSnackbarDuration.Long,
        )
        if (result == AppSnackbarResult.ActionPerformed) {
            viewModel.restoreSmsMsgList(targets)
        }
    }
}
