package io.github.magisk317.relay.desktop.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesktopMessagesTest {

    @Test
    fun `all locales expose the same keys`() {
        val zhCn = DesktopMessages.table(DesktopLocale.ZH_CN).keys
        val en = DesktopMessages.table(DesktopLocale.EN).keys
        val zhTw = DesktopMessages.table(DesktopLocale.ZH_TW).keys
        assertEquals(zhCn, en)
        assertEquals(zhCn, zhTw)
        assertTrue(zhCn.isNotEmpty(), "message tables must not be empty")
    }

    @Test
    fun `keys are unique across the canonical order`() {
        val keys = DesktopMessages.keys
        assertEquals(keys.size, keys.toSet().size, "duplicate message keys")
    }

    @Test
    fun `spot checks match the webUI strings`() {
        assertEquals("概览", DesktopMessages.t(DesktopLocale.ZH_CN, "layout.nav.overview"))
        assertEquals("Overview", DesktopMessages.t(DesktopLocale.EN, "layout.nav.overview"))
        // zh-TW spreads the zh-CN table in the source and only overrides what it spells traditionally.
        assertEquals("概览", DesktopMessages.t(DesktopLocale.ZH_TW, "layout.nav.overview"))
        assertEquals("繁體中文", DesktopMessages.t(DesktopLocale.ZH_TW, "locale.zh-TW"))
        assertEquals("Traditional Chinese", DesktopMessages.t(DesktopLocale.EN, "locale.zh-TW"))
    }

    @Test
    fun `placeholders are interpolated`() {
        val message = DesktopMessages.t(
            DesktopLocale.EN,
            "common.lastSeen",
            mapOf("time" to "5 minutes ago"),
        )
        assertEquals("Last seen 5 minutes ago", message)
    }

    @Test
    fun `zh-tw entries shadow the zh-cn base table`() {
        // zh-TW spreads zh-CN in the source; only the overrides differ.
        val overridden = DesktopMessages.zhCn.filter { (key, zh) -> DesktopMessages.zhTw[key] != zh }
        assertTrue(overridden.isNotEmpty(), "zh-TW carries traditional-character overrides")
    }

    @Test
    fun `unknown keys fall back to the key itself`() {
        assertEquals("no.such.key", DesktopMessages.t(DesktopLocale.EN, "no.such.key"))
    }

    @Test
    fun `locale enum round trips through tags`() {
        DesktopLocale.entries.forEach { locale ->
            assertNotNull(DesktopLocale.fromTag(locale.tag))
        }
        assertNull(DesktopLocale.fromTag("fr"))
    }
}
