package io.github.magisk317.relay.data.repository

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RemoteAgentRepositoryJsonMergeTest {

    @Test
    fun mergeRemoteConfigJson_keepsBaseWhenTopLevelSectionMissing() {
        val base = JsonParser.parseString(
            """
            {
              "general": {
                "moduleEnabled": true,
                "accordionMode": true
              },
              "verification": {
                "verificationFeaturesEnabled": true
              }
            }
            """.trimIndent(),
        ).asJsonObject
        val incoming = JsonParser.parseString(
            """
            {
              "verification": {
                "verificationFeaturesEnabled": false
              }
            }
            """.trimIndent(),
        ).asJsonObject

        val merged = mergeRemoteConfigJson(base, incoming)

        assertTrue(merged.getAsJsonObject("general").get("moduleEnabled").asBoolean)
        assertTrue(merged.getAsJsonObject("general").get("accordionMode").asBoolean)
        assertEquals(false, merged.getAsJsonObject("verification").get("verificationFeaturesEnabled").asBoolean)
    }

    @Test
    fun mergeRemoteConfigJson_mergesNestedObjectsWithoutDroppingSiblingFields() {
        val base = JsonParser.parseString(
            """
            {
              "general": {
                "moduleEnabled": true,
                "accordionMode": true
              }
            }
            """.trimIndent(),
        ).asJsonObject
        val incoming = JsonParser.parseString(
            """
            {
              "general": {
                "accordionMode": false
              }
            }
            """.trimIndent(),
        ).asJsonObject

        val merged = mergeRemoteConfigJson(base, incoming)
        val general = merged.getAsJsonObject("general")

        assertEquals(true, general.get("moduleEnabled").asBoolean)
        assertEquals(false, general.get("accordionMode").asBoolean)
    }
}
