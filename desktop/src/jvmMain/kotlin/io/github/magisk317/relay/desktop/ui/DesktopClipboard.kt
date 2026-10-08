package io.github.magisk317.relay.desktop.ui

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Copies text to the system clipboard. The webUI calls the async clipboard
 * API here; on the desktop a plain AWT call is enough and keeps the ports
 * free of platform APIs.
 */
fun copyToClipboard(text: String): Boolean = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}.isSuccess
