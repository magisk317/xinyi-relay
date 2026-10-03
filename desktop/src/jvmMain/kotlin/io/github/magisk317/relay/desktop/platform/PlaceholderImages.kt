package io.github.magisk317.relay.desktop.platform

import java.awt.Image
import java.awt.image.BufferedImage

/**
 * Runtime-generated tray art.
 *
 * A tray icon cannot be registered without an image, and this module ships no
 * icon binary (`desktop/src/jvmMain/resources` is deliberately empty until a
 * real asset lands), so a 16x16 flat square is drawn instead: it satisfies
 * `TrayIcon`'s non-null image contract and is unremarkable in any real
 * notification area. When a real tray icon ships, replace [tray] and drop the
 * call from both [AwtTray] and [AwtNotifier].
 */
internal object PlaceholderImages {
    fun tray(): Image = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB).also { image ->
        val graphics = image.createGraphics()
        graphics.color = java.awt.Color(0x1F, 0x6F, 0xEB)
        graphics.fillRect(0, 0, 16, 16)
        graphics.dispose()
    }
}
