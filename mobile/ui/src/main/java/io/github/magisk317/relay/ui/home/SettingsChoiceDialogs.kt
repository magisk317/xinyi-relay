package io.github.magisk317.relay.ui.home

import androidx.compose.runtime.Composable
import io.github.magisk317.relay.ui.common.SingleChoiceOptionDialog

@Composable
internal fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelectionChange: ((Int) -> Unit)? = null,
    onConfirm: (Int) -> Unit,
) {
    SingleChoiceOptionDialog(
        title = title,
        options = options,
        selectedIndex = selectedIndex,
        onDismiss = onDismiss,
        onSelectionChange = onSelectionChange,
        onConfirm = onConfirm,
    )
}
