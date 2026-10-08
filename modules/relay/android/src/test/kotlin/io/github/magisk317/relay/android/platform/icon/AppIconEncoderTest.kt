package io.github.magisk317.relay.android.platform.icon

import android.content.Context
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppIconEncoderTest {

    @Test
    fun resolveAppIcon_clearsIconWhenTemplateOmitsAppIcon() {
        val context = mockk<Context>(relaxed = true)

        assertEquals(
            "",
            AppIconEncoder.resolveAppIcon(
                context = context,
                packageName = "com.example.app",
                msgType = "app_notify",
                template = "content:{{SMS}}",
                currentAppIcon = "preset-icon",
            ),
        )
    }

    @Test
    fun resolveAppIcon_keepsExistingIconWhenTemplateUsesAppIcon() {
        val context = mockk<Context>(relaxed = true)

        assertEquals(
            "preset-icon",
            AppIconEncoder.resolveAppIcon(
                context = context,
                packageName = "com.example.app",
                msgType = "app_notify",
                template = "icon:{{APP_ICON}}",
                currentAppIcon = "preset-icon",
            ),
        )
    }
}
