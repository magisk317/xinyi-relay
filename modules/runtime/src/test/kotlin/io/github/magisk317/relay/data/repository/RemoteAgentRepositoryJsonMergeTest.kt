package io.github.magisk317.relay.data.repository

import io.github.magisk317.relay.contract.json.RelayJson
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    @Test
    fun applyConfigMutationBatch_replaceSenders_prunesSenderScopedRelations() {
        val base = RelayJson.parseElement(
            """
            {
              "senders":[{"id":1},{"id":2}],
              "rules":[{"senderId":1},{"senderId":2}],
              "notifyRoutes":[{"senderId":1},{"senderId":2}],
              "forwardFilters":[{"senderId":1},{"senderId":2}]
            }
            """.trimIndent(),
        ).jsonObject
        val mutation = RelayJson.parseElement(
            """
            {
              "operations":[
                {
                  "type":"replace_senders",
                  "senders":[{"id":2}],
                  "removedSenderIds":[1]
                }
              ]
            }
            """.trimIndent(),
        ).jsonObject

        val applied = applyConfigMutationBatch(base, mutation)
        requireNotNull(applied)

        assertEquals(1, applied.getValue("senders").jsonArray.size)
        assertEquals(1, applied.getValue("rules").jsonArray.size)
        assertEquals(1, applied.getValue("notifyRoutes").jsonArray.size)
        assertEquals(1, applied.getValue("forwardFilters").jsonArray.size)
        assertFalse(applied.getValue("rules").jsonArray.toString().contains("\"senderId\":1"))
    }

    @Test
    fun applyConfigMutationBatch_replaceDeviceApps_updatesOnlySelectedDeviceCatalog() {
        val base = RelayJson.parseElement(
            """
            {
              "deviceAppInfos":{
                "1":[{"packageName":"a"}],
                "2":[{"packageName":"b"}]
              }
            }
            """.trimIndent(),
        ).jsonObject
        val mutation = RelayJson.parseElement(
            """
            {
              "operations":[
                {
                  "type":"replace_device_apps",
                  "deviceId":2,
                  "apps":[{"packageName":"updated"}]
                }
              ]
            }
            """.trimIndent(),
        ).jsonObject

        val applied = applyConfigMutationBatch(base, mutation)
        requireNotNull(applied)
        val infos = applied.getValue("deviceAppInfos").jsonObject
        assertTrue(infos.getValue("1").toString().contains("\"packageName\":\"a\""))
        assertTrue(infos.getValue("2").toString().contains("\"packageName\":\"updated\""))
    }

    @Test
    fun computeDeviceAppCatalogDigest_usesSelectedDeviceCatalogWithStableOrdering() {
        val mirrorContent = RelayJson.parseElement(
            """
            {
              "deviceAppInfos":{
                "1":[{"packageName":"other"}],
                "2":[
                  {
                    "packageName":"z.app",
                    "label":"Zulu",
                    "blocked":true,
                    "forwarding":false,
                    "forwardingConfigured":true,
                    "notifyTemplate":"Z"
                  },
                  {
                    "packageName":"a.app",
                    "label":"Alpha",
                    "blocked":false,
                    "forwarding":true,
                    "forwardingConfigured":false,
                    "notifyTemplate":"A"
                  }
                ]
              }
            }
            """.trimIndent(),
        ).jsonObject

        val digest = computeDeviceAppCatalogDigest(mirrorContent, deviceId = "2")

        assertEquals(
            """[["a.app","Alpha",false,true,false,"A"],["z.app","Zulu",true,false,true,"Z"]]""",
            digest,
        )
    }
}
