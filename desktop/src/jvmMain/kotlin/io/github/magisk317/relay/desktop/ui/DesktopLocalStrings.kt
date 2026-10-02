package io.github.magisk317.relay.desktop.ui

import io.github.magisk317.relay.desktop.i18n.DesktopLocale

/**
 * Strings that exist only on the desktop (backend URL setup and the browser
 * handoff hint). The webUI itself never needs them because it talks to its
 * same-origin backend and authenticates through a form post.
 */
private val zhCn = mapOf(
    "backendUrl" to "后端地址",
    "backendUrlRequired" to "请先填写后端地址",
    "browserHandoffHint" to "将在系统浏览器中完成登录，完成后自动返回。",
)

private val en = mapOf(
    "backendUrl" to "Backend URL",
    "backendUrlRequired" to "Enter the backend URL first",
    "browserHandoffHint" to "You will finish signing in through the system browser, then come back here.",
)

private val zhTw = mapOf(
    "backendUrl" to "後端位址",
    "backendUrlRequired" to "請先填寫後端位址",
    "browserHandoffHint" to "將在系統瀏覽器中完成登入，完成後自動返回。",
)

object DesktopLocalStrings {
    fun t(locale: DesktopLocale, key: String): String = when (locale) {
        DesktopLocale.ZH_CN -> zhCn[key] ?: en[key] ?: key
        DesktopLocale.ZH_TW -> zhTw[key] ?: zhCn[key] ?: en[key] ?: key
        DesktopLocale.EN -> en[key] ?: key
    }
}
