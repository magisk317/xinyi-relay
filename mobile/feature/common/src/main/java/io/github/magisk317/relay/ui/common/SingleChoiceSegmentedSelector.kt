package io.github.magisk317.relay.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.magisk317.uikit.surface.AppSegmentedOption
import io.github.magisk317.uikit.surface.AppSingleChoiceSegmentedSelector

data class SegmentedOption<T>(
    val value: T,
    val label: String,
)

@Composable
fun <T> SingleChoiceSegmentedSelector(
    options: List<SegmentedOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppSingleChoiceSegmentedSelector(
        options = options.map { AppSegmentedOption(it.value, it.label) },
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
    )
}
