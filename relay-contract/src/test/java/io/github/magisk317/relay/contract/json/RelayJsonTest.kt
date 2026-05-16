package io.github.magisk317.relay.contract.json

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun legacyGsonJson_keepsSerializedNameContract() {
        val decoded = LegacyGsonJson.fromJson("""{"wire_name":"demo"}""", LegacySample::class.java)

        assertEquals("demo", decoded.wireName)
        assertTrue(LegacyGsonJson.toJson(decoded).contains(""""wire_name":"demo""""))
    }

    @Serializable
    private data class Sample(
        val name: String,
        val enabled: Boolean = true,
    )

    private data class LegacySample(
        @SerializedName("wire_name")
        val wireName: String = "",
    )
}
