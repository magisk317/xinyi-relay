package io.github.magisk317.relay.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun DismissibleSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    snackbar: @Composable (SnackbarData) -> Unit = { data ->
        DefaultDismissibleSnackbar(data = data)
    },
) {
    io.github.magisk317.uikit.common.DismissibleSnackbarHost(
        hostState = hostState,
        modifier = modifier,
        snackbar = snackbar,
    )
}

@Composable
private fun DefaultDismissibleSnackbar(
    data: SnackbarData,
) {
    androidx.compose.material3.Snackbar(
        action = {
            data.visuals.actionLabel?.let { label ->
                androidx.compose.material3.TextButton(onClick = { data.performAction() }) {
                    androidx.compose.material3.Text(label)
                }
            }
        },
        dismissAction = if (data.visuals.withDismissAction) {
            {
                androidx.compose.material3.IconButton(onClick = { data.dismiss() }) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                    )
                }
            }
        } else {
            null
        },
    ) {
        androidx.compose.material3.Text(data.visuals.message)
    }
}
