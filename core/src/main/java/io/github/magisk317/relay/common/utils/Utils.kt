package io.github.magisk317.relay.common.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import io.github.magisk317.relay.core.R
import java.util.*

/**
 * Other Utils
 */
object Utils {

    @JvmStatic
    fun showWebPage(context: Context, url: String) {
        try {
            val cti = CustomTabsIntent.Builder().build()
            cti.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            cti.launchUrl(context, Uri.parse(url))
        } catch (ignored: Exception) {
            Toast.makeText(context, R.string.browser_install_or_enable_prompt, Toast.LENGTH_SHORT).show()
        }
    }

    @JvmStatic
    fun copyToClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("relay", text))
    }

    private fun getLanguagePath(): String {
        val locale = Locale.getDefault()
        val language = locale.language
        val country = locale.country
        var result = "en"
        if ("zh" == language) {
            result = if ("CN".equals(country, ignoreCase = true)) {
                "zh-CN"
            } else if ("HK".equals(country, ignoreCase = true) || "TW".equals(country, ignoreCase = true)) {
                "zh-TW"
            } else {
                "zh-CN"
            }
        }
        return result
    }

    @JvmStatic
    fun getProjectDocUrl(docBaseUrl: String, docPath: String): String = "$docBaseUrl/${getLanguagePath()}/$docPath"

    @JvmStatic
    fun isValidFilename(filename: String?): Boolean {
        if (filename.isNullOrBlank()) {
            return false
        }

        val trimmed = filename.trim()
        if ("." == trimmed || ".." == trimmed) {
            return false
        }

        for (c in filename) {
            if (!isValidFilenameChar(c)) {
                return false
            }
        }
        return true
    }

    @JvmStatic
    fun saveImageToGallery(context: Context, resId: Int, fileName: String) {
        val bitmap = BitmapFactory.decodeResource(context.resources, resId)
        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (imageUri != null) {
            try {
                resolver.openOutputStream(imageUri)?.use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
                val appNameResId = if (fileName.contains(
                        "alipay",
                    )
                ) {
                    R.string.dialog_donate_alipay
                } else {
                    R.string.dialog_donate_wechat
                }
                val appName = context.getString(appNameResId).replace(Regex("\\(.*?\\)"), "").trim()
                Toast.makeText(
                    context,
                    context.getString(R.string.save_to_gallery_success, appName),
                    Toast.LENGTH_SHORT,
                ).show()
                if (fileName.contains("alipay")) {
                    PackageUtils.startAlipayActivity(context)
                } else if (fileName.contains("wechat")) {
                    PackageUtils.startWechatActivity(context)
                }
            } catch (ignored: Exception) {
                Toast.makeText(context, R.string.save_to_gallery_failed, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, R.string.save_to_gallery_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private const val CONTROL_CHAR_LIMIT = 0x1f
    private const val DELETE_CHAR = 0x7f

    private fun isValidFilenameChar(c: Char): Boolean {
        // check control characters
        if (c.code <= CONTROL_CHAR_LIMIT || c.code == DELETE_CHAR) {
            return false
        }

        // check special characters
        return when (c) {
            '"', '*', '/', '\\', '<', '>', '|', '?', ',', ';', ':' -> false
            else -> true
        }
    }
}
