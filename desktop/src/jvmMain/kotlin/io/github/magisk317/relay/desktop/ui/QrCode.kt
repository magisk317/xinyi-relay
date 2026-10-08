package io.github.magisk317.relay.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import java.net.URLEncoder
import kotlin.math.min

/** Encoded at a fixed pixel size; the canvas scales it to the rendered size. */
private const val QR_PIXEL_SIZE = 512

/**
 * Renders [value] as a QR code, the desktop counterpart of the webUI's
 * QRCodeSVG: black modules over white with the quiet zone included, like
 * the includeMargin prop.
 */
@Composable
fun QrCodeImage(
    value: String,
    modifier: Modifier = Modifier,
    qrSize: Dp = 180.dp,
) {
    val matrix = remember(value) { encodeQrCode(value) }
    Canvas(modifier = modifier.size(qrSize)) {
        val modules = matrix ?: return@Canvas
        drawRect(color = Color.White, size = this.size)
        val cell = min(this.size.width, this.size.height) / modules.width
        val originX = (this.size.width - cell * modules.width) / 2f
        val originY = (this.size.height - cell * modules.height) / 2f
        for (y in 0 until modules.height) {
            for (x in 0 until modules.width) {
                if (!modules.get(x, y)) continue
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(originX + x * cell, originY + y * cell),
                    size = Size(cell, cell),
                )
            }
        }
    }
}

/** Encode failures (payload too long for a QR code) yield no image. */
private fun encodeQrCode(value: String): BitMatrix? =
    runCatching { QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, QR_PIXEL_SIZE, QR_PIXEL_SIZE) }
        .getOrNull()

/**
 * Builds the one-shot bind payload the Android app scans. The webUI derives the
 * console address from window.location.origin; the desktop uses its active
 * profile URL instead.
 */
fun buildBindQrValue(code: String, baseUrl: String): String =
    "xinyi-relay://bind?code=" + URLEncoder.encode(code, "UTF-8") +
        "&base_url=" + URLEncoder.encode(baseUrl.trimEnd('/'), "UTF-8")

