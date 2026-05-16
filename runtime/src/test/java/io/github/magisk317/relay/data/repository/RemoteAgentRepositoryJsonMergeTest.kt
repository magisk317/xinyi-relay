package io.github.magisk317.relay.data.repository

import io.github.magisk317.relay.contract.json.RelayJson
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RemoteAgentRepositoryJsonMergeTest {

    @Test
    fun mergeRemoteConfigJson_keepsBaseWhenTopLevelSectionMissing() {
        val base = RelayJson.parseElement(
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
        ).jsonObject
        val incoming = RelayJson.parseElement(
            """
            {
              "verification": {
                "verificationFeaturesEnabled": false
              }
            }
            """.trimIndent(),
        ).jsonObject

        val merged = mergeRemoteConfigJson(base, incoming)

        assertTrue(merged.getValue("general").jsonObject.getValue("moduleEnabled").jsonPrimitive.boolean)
        assertTrue(merged.getValue("general").jsonObject.getValue("accordionMode").jsonPrimitive.boolean)
        assertEquals(
            false,
            merged.getValue("verification").jsonObject.getValue("verificationFeaturesEnabled").jsonPrimitive.boolean,
        )
    }

    @Test
    fun mergeRemoteConfigJson_mergesNestedObjectsWithoutDroppingSiblingFields() {
        val base = RelayJson.parseElement(
            """
            {
              "general": {
                "moduleEnabled": true,
                "accordionMode": true
              }
            }
            """.trimIndent(),
        ).jsonObject
        val incoming = RelayJson.parseElement(
            """
            {
              "general": {
                "accordionMode": false
              }
            }
            """.trimIndent(),
        ).jsonObject

        val merged = mergeRemoteConfigJson(base, incoming)
        val general = merged.getValue("general").jsonObject

        assertEquals(true, general.getValue("moduleEnabled").jsonPrimitive.boolean)
        assertEquals(false, general.getValue("accordionMode").jsonPrimitive.boolean)
    }
}
