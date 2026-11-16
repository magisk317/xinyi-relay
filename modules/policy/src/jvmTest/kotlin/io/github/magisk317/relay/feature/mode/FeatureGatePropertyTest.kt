package io.github.magisk317.relay.feature.mode

import io.github.magisk317.relay.feature.mode.FeatureGate.Feature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FeatureGatePropertyTest {
    private val flags = listOf(false, true)

    @Test
    fun `every feature obeys the runtime and root truth table`() {
        for (xposed in flags) for (root in flags) for (nonRoot in flags) for (feature in Feature.entries) {
            val expected = when (feature) {
                Feature.ROOT_DB_CATCHUP, Feature.ROOT_DB_CATCHUP_WRITEBACK -> root
                Feature.FORCE_STOP_RECOVERY, Feature.FORCE_STOP_RECOVERY_RELAUNCH_ONCE -> xposed && root
                Feature.COPY_CODE_TO_CLIPBOARD -> xposed || nonRoot
                else -> xposed
            }
            assertEquals(expected, FeatureGate.isAvailable(feature, xposed, root, nonRoot), "$feature x=$xposed root=$root nonRoot=$nonRoot")
        }
    }

    @Test
    fun `both allAvailable overloads agree on every feature subset`() {
        val features = Feature.entries.toTypedArray()
        for (mask in 0 until (1 shl features.size)) {
            val selected = features.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toTypedArray()
            for (xposed in flags) for (nonRoot in flags) {
                assertEquals(selected.all { FeatureGate.isAvailable(it, xposed, nonRootActive = nonRoot) },
                    FeatureGate.allAvailable(xposed, nonRoot, *selected))
                for (root in flags) {
                    assertEquals(selected.all { FeatureGate.isAvailable(it, xposed, root, nonRoot) },
                        FeatureGate.allAvailable(xposed, root, nonRoot, *selected))
                }
            }
        }
    }
}
