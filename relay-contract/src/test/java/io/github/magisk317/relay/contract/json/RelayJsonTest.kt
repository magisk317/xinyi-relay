package io.github.magisk317.relay.contract.json

import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RelayJsonTest {

    @Test
    fun relayJson_ignoresUnknownKeysAndKeepsDefaults() {
        val decoded = RelayJson.decode(
            Sample.serializer(),
            """{"name":"demo","ignored":true}""",
        )

        assertEquals(Sample(name = "demo"), decoded)
        assertEquals("""{"name":"demo","enabled":true}""", RelayJson.encode(Sample.serializer(), decoded))
    }

    @Serializable
    private data class Sample(
        val name: String,
        val enabled: Boolean = true,
    )
}
