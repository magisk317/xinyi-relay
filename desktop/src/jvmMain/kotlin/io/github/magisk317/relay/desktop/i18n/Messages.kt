package io.github.magisk317.relay.desktop.i18n

/**
 * Locales offered by the desktop shell, matching the webUI locale setting.
 */
enum class DesktopLocale(val tag: String) {
    EN("en"),
    ZH_CN("zh-CN"),
    ZH_TW("zh-TW"),
    ;

    companion object {
        fun fromTag(tag: String): DesktopLocale? = entries.firstOrNull { it.tag == tag }
    }
}

/**
 * Message tables mechanically ported from frontend/webui/src/i18n.tsx.
 *
 * Keep in sync with the webUI source; the port is mechanical so the desktop copy
 * stays byte-identical to what users see on the web console.
 */
object DesktopMessages {
    val keys: List<String> = zhCn.keys.toList()

    fun table(locale: DesktopLocale): Map<String, String> = when (locale) {
        DesktopLocale.ZH_CN -> zhCn
        DesktopLocale.EN -> en
        DesktopLocale.ZH_TW -> zhTw
    }

    /**
     * Looks up [key] for [locale], falling back to zh-CN then English when a
     * translation is missing, and interpolates `{name}` placeholders from [params].
     */
    fun t(locale: DesktopLocale, key: String, params: Map<String, Any?> = emptyMap()): String {
        val raw = table(locale)[key] ?: zhCn[key] ?: en[key] ?: key
        if (params.isEmpty()) return raw
        var out = raw
        for ((name, value) in params) {
            out = out.replace("{" + name + "}", value?.toString() ?: "")
        }
        return out
    }
}
