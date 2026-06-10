package io.github.magisk317.relay.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class SegmentedOption<T>(
    val value: T,
    val label: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SingleChoiceSegmentedSelector(
    options: List<SegmentedOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.fillMaxWidth(),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option.value == selected
            val weight by animateFloatAsState(
                targetValue = if (isSelected) 3f else 1f,
                label = "weightAnim",
            )
            SegmentedButton(
                selected = isSelected,
                onClick = { onSelect(option.value) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size,
                ),
                modifier = Modifier
                    .weight(weight)
                    .heightIn(min = 40.dp),
                icon = {},
                label = {
                    CenteredChipText(text = option.label)
                },
            )
        }
    }
}
