package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import dev.mokkery.MockMode.autofill
import dev.mokkery.mock
import io.github.magisk317.smscode.verification.SmsRoleStateResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SmsRoleStateResolverTest {

    @Test
    fun resolve_returnsProvidersOutput() {
        val context = mock<Context>(autofill)
        val resolver = SmsRoleStateResolver(
            defaultSmsProvider = { receivedContext ->
                assertEquals(context, receivedContext)
                "com.android.messaging"
            },
            roleHoldersProvider = { receivedContext ->
                assertEquals(context, receivedContext)
                listOf("com.android.messaging", "io.github.magisk317.relay")
            },
        )

        val roleState = resolver.resolve(context)

        assertEquals("com.android.messaging", roleState.defaultSms)
        assertEquals(listOf("com.android.messaging", "io.github.magisk317.relay"), roleState.roleHolders)
    }
}
