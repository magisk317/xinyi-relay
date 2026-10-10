package io.github.magisk317.relay.ui.sender.forms

import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.sender.SenderSettingDraft
import io.github.magisk317.relay.sender.SenderSettingDrafts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for the progressive disclosure of the Matrix form: the proxy fields
 * follow the selected proxy mode, and the credentials follow the authenticator
 * toggle.
 */
class MatrixConfigFormVisibilityTest {

    private fun matrixDraft(
        proxyType: String? = null,
        proxyAuthenticator: Boolean? = null,
    ): SenderSettingDraft {
        var draft = SenderSettingDrafts.emptyWithDefaults(SenderType.MATRIX)
        proxyType?.let { draft = draft.withString("proxyType", it) }
        proxyAuthenticator?.let { draft = draft.withBoolean("proxyAuthenticator", it) }
        return draft
    }

    private fun visibleFieldNames(draft: SenderSettingDraft): Set<String> =
        MatrixVisibleFields.filter { it.visible(draft) }.map { it.name }.toSet()

    private fun proxyDependentFields(): Set<String> = setOf(
        "proxyHost",
        "proxyPort",
        "proxyAuthenticator",
        "proxyUsername",
        "proxyPassword",
    )

    @Test
    fun `direct proxy hides every proxy dependent field`() {
        val visible = visibleFieldNames(matrixDraft(proxyType = "DIRECT"))

        assertTrue(visible.contains("proxyType"))
        assertEquals(emptySet<String>(), visible intersect proxyDependentFields())
    }

    @Test
    fun `http proxy shows host, port and authenticator`() {
        val visible = visibleFieldNames(matrixDraft(proxyType = "HTTP"))

        assertTrue(visible.containsAll(setOf("proxyType", "proxyHost", "proxyPort", "proxyAuthenticator")))
        assertFalse(visible.contains("proxyUsername"))
        assertFalse(visible.contains("proxyPassword"))
    }

    @Test
    fun `socks proxy shows host, port and authenticator`() {
        val visible = visibleFieldNames(matrixDraft(proxyType = "SOCKS"))

        assertTrue(visible.containsAll(setOf("proxyType", "proxyHost", "proxyPort", "proxyAuthenticator")))
        assertFalse(visible.contains("proxyUsername"))
        assertFalse(visible.contains("proxyPassword"))
    }

    @Test
    fun `credentials appear once the authenticator is enabled`() {
        val visible = visibleFieldNames(
            matrixDraft(proxyType = "HTTP", proxyAuthenticator = true),
        )

        assertTrue(visible.containsAll(setOf("proxyHost", "proxyPort", "proxyAuthenticator")))
        assertTrue(visible.containsAll(setOf("proxyUsername", "proxyPassword")))
    }

    @Test
    fun `credentials disappear again when the authenticator is disabled`() {
        val visible = visibleFieldNames(
            matrixDraft(proxyType = "HTTP", proxyAuthenticator = false),
        )

        assertFalse(visible.contains("proxyUsername"))
        assertFalse(visible.contains("proxyPassword"))
    }

    @Test
    fun `blank proxy type is treated as direct`() {
        val visible = visibleFieldNames(matrixDraft(proxyType = "  "))

        assertEquals(emptySet<String>(), visible intersect proxyDependentFields())
    }

    @Test
    fun `non proxy fields stay visible in every proxy mode`() {
        val alwaysVisible = setOf(
            "homeserver",
            "username",
            "password",
            "roomId",
            "messageType",
            "titleTemplate",
            "proxyType",
        )

        listOf("DIRECT", "HTTP", "SOCKS").forEach { proxyType ->
            val visible = visibleFieldNames(matrixDraft(proxyType = proxyType))
            assertTrue(
                visible.containsAll(alwaysVisible),
                "$proxyType mode hid ${alwaysVisible - visible}",
            )
        }
    }

    @Test
    fun `default draft starts on a direct proxy with the proxy fields hidden`() {
        val draft = SenderSettingDrafts.emptyWithDefaults(SenderType.MATRIX)
        val visible = visibleFieldNames(draft)

        assertEquals("DIRECT", draft.string("proxyType"))
        assertEquals(emptySet<String>(), visible intersect proxyDependentFields())
    }
}
