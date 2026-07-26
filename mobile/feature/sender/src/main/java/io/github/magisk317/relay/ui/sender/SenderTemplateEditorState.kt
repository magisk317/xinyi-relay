package io.github.magisk317.relay.ui.sender

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

@Stable
internal class SenderTemplateEditorState(
    initialTemplate: String,
    private val renderPreview: (String) -> String,
) {
    var value by mutableStateOf(TextFieldValue(initialTemplate))
        private set
    var isFocused by mutableStateOf(false)
        private set
    var preview by mutableStateOf(renderPreview(initialTemplate))
        private set

    fun onValueChange(newValue: TextFieldValue) {
        value = normalizeTokenDeletion(value, newValue)
    }

    fun onFocusChanged(focused: Boolean) {
        if (isFocused && !focused) refreshPreview()
        isFocused = focused
    }

    fun insertToken(token: String) {
        val start = value.selection.start.coerceIn(0, value.text.length)
        val end = value.selection.end.coerceIn(0, value.text.length)
        val newText = value.text.replaceRange(start, end, token)
        value = value.copy(text = newText, selection = TextRange(start + token.length))
        if (!isFocused) refreshPreview()
    }

    fun replaceTemplate(template: String) {
        value = TextFieldValue(template, selection = TextRange(template.length))
        refreshPreview()
    }

    fun refreshPreview() {
        preview = renderPreview(value.text)
    }

    private fun normalizeTokenDeletion(
        oldValue: TextFieldValue,
        newValue: TextFieldValue,
    ): TextFieldValue {
        val oldSelection = oldValue.selection
        if (oldSelection.start != oldSelection.end) return newValue
        if (newValue.text.length != oldValue.text.length - 1) return newValue

        val oldCursor = oldSelection.start
        val isBackspace = newValue.selection.start == (oldCursor - 1).coerceAtLeast(0)
        val removeIndex = if (isBackspace) oldCursor - 1 else oldCursor
        if (removeIndex !in oldValue.text.indices) return newValue

        val token = templateTokenRegex.findAll(oldValue.text).firstOrNull { match ->
            removeIndex in match.range
        } ?: return newValue
        val merged = oldValue.text.removeRange(token.range.first, token.range.last + 1)
        return TextFieldValue(
            text = merged,
            selection = TextRange(token.range.first.coerceAtMost(merged.length)),
        )
    }
}

@Composable
internal fun rememberSenderTemplateEditorState(
    initialTemplate: String,
    vararg keys: Any?,
    renderPreview: (String) -> String,
): SenderTemplateEditorState = remember(initialTemplate, *keys) {
    SenderTemplateEditorState(initialTemplate, renderPreview)
}
