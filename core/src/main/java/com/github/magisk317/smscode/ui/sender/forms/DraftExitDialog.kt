package com.github.magisk317.smscode.ui.sender.forms

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun DraftExitDialog(
    onSaveDraft: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    LaunchedEffect(Unit) {
        onSaveDraft()
        onCancel()
    }
}
