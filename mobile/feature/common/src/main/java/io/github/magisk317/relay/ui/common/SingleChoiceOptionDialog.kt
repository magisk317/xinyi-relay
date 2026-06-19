package io.github.magisk317.relay.ui.common

import androidx.compose.runtime.Composable

@Composable
fun SingleChoiceOptionDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelectionChange: ((Int) -> Unit)? = null,
    onConfirm: (Int) -> Unit,
) {
    io.github.magisk317.uikit.surface.AppSelectionDialog(
        title = title,
        options = options,
        selectedIndex = selectedIndex,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        onSelectionChange = onSelectionChange,
    )
}
