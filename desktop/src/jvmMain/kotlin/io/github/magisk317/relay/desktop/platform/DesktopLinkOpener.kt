package io.github.magisk317.relay.desktop.platform

import java.awt.Desktop
import java.net.URI

/**
 * Opens URLs in the user's default browser.
 *
 * The webUI hands link activation to the browser chrome; the desktop shell has
 * no equivalent, so anything the console renders as a link must go through
 * here. The scheme allow-list is the point of the interface: `Desktop.browse`
 * will happily hand `file:`, `jar:` or a registered custom scheme to the OS,
 * and the console renders URLs that arrive from the backend. Only http and
 * https are forwarded; everything else is refused rather than delegated.
 */
interface DesktopLinkOpener {

    /**
     * Opens [url] externally.
     *
     * @return true when the OS accepted the URL (the user still might close
     *   the tab), false when the URL was refused (scheme not allow-listed) or
     *   the platform had no browser association to hand it to.
     */
    fun open(url: String): Boolean
}

/**
 * [DesktopLinkOpener] over `java.awt.Desktop.browse`, with the OS call behind
 * an injectable lambda so the headless CI case (no Desktop support) and the
 * scheme-refusal path are both testable without a display.
 */
class AwtLinkOpener(
    private val browse: (URI) -> Unit = { uri -> Desktop.getDesktop().browse(uri) },
) : DesktopLinkOpener {

    override fun open(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() !in ALLOWED_SCHEMES) return false
        return runCatching { browse(uri) }.isSuccess
    }

    companion object {
        private val ALLOWED_SCHEMES = setOf("http", "https")
    }
}
