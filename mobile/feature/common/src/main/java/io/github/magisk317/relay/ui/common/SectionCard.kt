package io.github.magisk317.relay.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.contract.constant.RelayAppConst

@Composable
fun SectionCard(
    title: String,
    summary: String = "",
    accordionMode: Boolean,
    sectionExpanded: Boolean,
    onExpandedChange: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RelayAppConst.PADDING_SMALL.dp),
    ) {
        io.github.magisk317.uikit.preference.SectionCard(
            title = title,
            summary = summary,
            accordionMode = accordionMode,
            sectionExpanded = sectionExpanded,
            onExpandedChange = onExpandedChange,
            content = content,
        )
    }
}
