package io.github.magisk317.relay.desktop.i18n

import java.util.Locale
import java.util.prefs.Preferences

/**
 * Locale setting mirroring the webUI `relay-webui-locale` preference: an
 * explicit override, or "system" which follows the JVM locale and the
 * language tag reported by the backend session.
 */
enum class LocaleSetting(val tag: String) {
    SYSTEM("system"),
    EN("en"),
    ZH_CN("zh-CN"),
    ZH_TW("zh-TW"),
    ;

    fun toDesktopLocale(): DesktopLocale = when (this) {
        SYSTEM -> DesktopLocale.EN
        EN -> DesktopLocale.EN
        ZH_CN -> DesktopLocale.ZH_CN
        ZH_TW -> DesktopLocale.ZH_TW
    }

    companion object {
        fun fromTag(tag: String): LocaleSetting? = entries.firstOrNull { it.tag == tag }
    }
}

object LocalePreference {
    private const val STORAGE_KEY = "relay-webui-locale"

    private val prefs: Preferences = Preferences.userNodeForPackage(LocalePreference::class.java)

    fun load(): LocaleSetting =
        LocaleSetting.fromTag(prefs.get(STORAGE_KEY, LocaleSetting.SYSTEM.tag)) ?: LocaleSetting.SYSTEM

    fun save(setting: LocaleSetting) {
        prefs.put(STORAGE_KEY, setting.tag)
        prefs.flush()
    }

    /**
     * Resolves the effective locale the same way the webUI does: an explicit
     * setting wins, otherwise the server language tag, otherwise the system
     * locale, defaulting to English.
     */
    fun resolve(setting: LocaleSetting, serverLanguageTag: String = ""): DesktopLocale {
        if (setting != LocaleSetting.SYSTEM) return setting.toDesktopLocale()
        resolveLanguageTagLocale(serverLanguageTag)?.let { return it }
        return fromSystemLocale()
    }

    fun fromSystemLocale(): DesktopLocale {
        val tag = "${Locale.getDefault().language}-${Locale.getDefault().country}"
        return resolveLanguageTagLocale(tag) ?: DesktopLocale.EN
    }

    fun resolveLanguageTagLocale(tag: String): DesktopLocale? {
        val normalized = tag.replace('_', '-').lowercase(Locale.ROOT)
        return when {
            normalized.startsWith("zh-tw") ||
                normalized.startsWith("zh-hk") ||
                normalized.startsWith("zh-hant") ||
                normalized.startsWith("zh-mo") -> DesktopLocale.ZH_TW
            normalized.startsWith("zh") -> DesktopLocale.ZH_CN
            normalized.startsWith("en") -> DesktopLocale.EN
            else -> null
        }
    }
}
