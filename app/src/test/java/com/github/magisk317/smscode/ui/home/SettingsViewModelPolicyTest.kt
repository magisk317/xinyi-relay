package com.github.magisk317.smscode.ui.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingsViewModelPolicyTest {

    @Test
    fun resolvePreferredUpdateEvent_returnsPlayForPlayInstall() {
        assertEquals(SettingsEvent.StartPlayUpdate, resolvePreferredUpdateEvent(installedFromPlay = true))
    }

    @Test
    fun resolvePreferredUpdateEvent_returnsGithubForNonPlayInstall() {
        assertEquals(SettingsEvent.StartGithubUpdateCheck, resolvePreferredUpdateEvent(installedFromPlay = false))
    }
}
