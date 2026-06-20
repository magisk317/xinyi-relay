package io.github.magisk317.relay.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import io.github.magisk317.relay.core.R

@Composable
fun SingleChoiceOptionDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelectionChange: ((Int) -> Unit)? = null,
    onConfirm: (Int) -> Unit,
) {
    var currentIndex by remember(title, options, selectedIndex) {
        mutableIntStateOf(selectedIndex.coerceIn(0, (options.lastIndex).coerceAtLeast(0)))
    }

    io.github.magisk317.uikit.surface.AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            LazyColumn {
                itemsIndexed(options) { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) {
                                currentIndex = index
                                onSelectionChange?.invoke(index)
                            }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        io.github.magisk317.uikit.preference.AppRadioButton(
                            selected = index == currentIndex,
                            onClick = {
                                currentIndex = index
                                onSelectionChange?.invoke(index)
                            },
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = option)
                    }
                }
            }
        },
        confirmButton = {
            io.github.magisk317.uikit.surface.AppTextButton(
                text = stringResource(R.string.confirm),
                onClick = { onConfirm(currentIndex) },
            )
        },
        dismissButton = {
            io.github.magisk317.uikit.surface.AppTextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
            )
        },
    )
}
