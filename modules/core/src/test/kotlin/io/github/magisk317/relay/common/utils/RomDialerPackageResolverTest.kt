package io.github.magisk317.relay.common.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RomDialerPackageResolverTest {

    @Test
    fun `classify hyperos profile as hyper os`() {
        val profile = RomProfile(
            manufacturer = "Xiaomi",
            brand = "Redmi",
            display = "OS3.0.44.0.WPCCNXM",
            miuiVersionName = "V816",
            miOsVersionName = "OS3.0",
        )

        assertEquals(RomFamily.HYPER_OS, RomDialerPackageResolver.classify(profile))
        assertEquals(
            "com.android.contacts",
            RomDialerPackageResolver.strategyFor(profile).preferredPackages.first(),
        )
    }

    @Test
    fun `classify lineage profile as aosp like`() {
        val profile = RomProfile(
            manufacturer = "Google",
            brand = "google",
            display = "lineage_cheetah-userdebug 15 AP4A",
            miuiVersionName = "",
            miOsVersionName = "",
        )

        assertEquals(RomFamily.AOSP_LIKE, RomDialerPackageResolver.classify(profile))
        assertEquals(
            "com.google.android.dialer",
            RomDialerPackageResolver.strategyFor(profile).preferredPackages.first(),
        )
    }

    @Test
    fun `call like labels cover chinese and english variants`() {
        assertTrue(RomDialerPackageResolver.isCallLikeLabel("电话"))
        assertTrue(RomDialerPackageResolver.isCallLikeLabel("未接来电"))
        assertTrue(RomDialerPackageResolver.isCallLikeLabel("Phone"))
        assertTrue(RomDialerPackageResolver.isCallLikeLabel("Dialer"))
    }
}
