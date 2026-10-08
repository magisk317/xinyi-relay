package io.github.magisk317.relay.ui.sender

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import io.github.magisk317.uikit.surface.rememberSaveableTextFieldState

/**
 * Call-site owned template editor state backed by a kit [TextFieldState], so the
 * shared body renders through the dual-track AppTextField. The token-aware
 * deletion rule lives in an [InputTransformation] and mirrors the previous
 * TextFieldValue diff logic: originalText/originalSelection are compared against
 * the post-edit buffer to decide whether a whole {{token}} was backspaced.
 */
@Stable
internal class SenderTemplateEditorState(
    val state: TextFieldState,
    private val renderPreview: (String) -> String,
) {
    var isFocused by mutableStateOf(false)
        private set
    var preview by mutableStateOf(renderPreview(state.text.toString()))
        private set

    val inputTransformation: InputTransformation = TemplateTokenDeletionTransformation

    val text: String
        get() = state.text.toString()

    fun onFocusChanged(focused: Boolean) {
        if (isFocused && !focused) refreshPreview()
        isFocused = focused
    }

    fun insertToken(token: String) {
        state.edit {
            val start = selection.start.coerceIn(0, length)
            val end = selection.end.coerceIn(0, length)
            replace(start, end, token)
            selection = TextRange(start + token.length)
        }
        if (!isFocused) refreshPreview()
    }

    fun replaceTemplate(template: String) {
        state.edit {
            replace(0, length, template)
            selection = TextRange(template.length)
        }
        refreshPreview()
    }

    fun refreshPreview() {
        preview = renderPreview(state.text.toString())
    }
}

private val TemplateTokenDeletionTransformation = InputTransformation {
    val oldText = originalText.toString()
    val newText = toString()
    val oldSelection = originalSelection
    if (!oldSelection.collapsed) return@InputTransformation
    if (newText.length != oldText.length - 1) return@InputTransformation

    val oldCursor = oldSelection.start
    val isBackspace = selection.start == (oldCursor - 1).coerceAtLeast(0)
    val removeIndex = if (isBackspace) oldCursor - 1 else oldCursor
    if (removeIndex !in oldText.indices) return@InputTransformation

    val token = templateTokenRegex.findAll(oldText).firstOrNull { match ->
        removeIndex in match.range
    } ?: return@InputTransformation
    val merged = oldText.removeRange(token.range.first, token.range.last + 1)
    replace(0, length, merged)
    selection = TextRange(token.range.first.coerceAtMost(merged.length))
}

@Composable
internal fun rememberSenderTemplateEditorState(
    initialTemplate: String,
    vararg keys: Any?,
    renderPreview: (String) -> String,
): SenderTemplateEditorState {
    val state = rememberSaveableTextFieldState(
        initialTemplate,
        TextRange(initialTemplate.length),
        initialTemplate,
        *keys,
    )
    return remember(state, *keys) {
        SenderTemplateEditorState(state, renderPreview)
    }
}
