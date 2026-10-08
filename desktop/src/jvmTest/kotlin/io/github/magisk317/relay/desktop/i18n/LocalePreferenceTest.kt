package io.github.magisk317.relay.desktop.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LocalePreferenceTest {

    @Test
    fun `explicit setting wins over system locale`() {
        assertEquals(
            DesktopLocale.ZH_TW,
            LocalePreference.resolve(LocaleSetting.ZH_TW, serverLanguageTag = "en"),
        )
        assertEquals(
            DesktopLocale.EN,
            LocalePreference.resolve(LocaleSetting.EN, serverLanguageTag = "zh-CN"),
        )
    }

    @Test
    fun `system setting follows the server language tag first`() {
        assertEquals(
            DesktopLocale.ZH_CN,
            LocalePreference.resolve(LocaleSetting.SYSTEM, serverLanguageTag = "zh-CN"),
        )
        assertEquals(
            LocalePreference.fromSystemLocale(),
            LocalePreference.resolve(LocaleSetting.SYSTEM, serverLanguageTag = "ja-JP"),
        )
    }

    @Test
    fun `server tag mapping handles locales the backend may report`() {
        assertEquals(DesktopLocale.ZH_TW, LocalePreference.resolveLanguageTagLocale("zh-TW"))
        assertEquals(DesktopLocale.ZH_TW, LocalePreference.resolveLanguageTagLocale("zh_Hant_TW"))
        assertEquals(DesktopLocale.ZH_CN, LocalePreference.resolveLanguageTagLocale("zh-Hans-CN"))
        assertEquals(DesktopLocale.EN, LocalePreference.resolveLanguageTagLocale("en"))
        assertNull(LocalePreference.resolveLanguageTagLocale("ja-JP"))
    }

    @Test
    fun `locale setting round trips through tags`() {
        LocaleSetting.entries.forEach { setting ->
            assertEquals(setting, LocaleSetting.fromTag(setting.tag))
        }
        assertNull(LocaleSetting.fromTag("fr"))
    }
}
