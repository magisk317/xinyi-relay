package io.github.magisk317.relay.matrix.e2ee

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MatrixLoginSessionTest {

    @Test
    fun `login session cache is reused for the fixed relay device on the same homeserver`() {
        assertTrue(
            MatrixE2eeSendPolicy.shouldReuseLoginSession(
                cachedDeviceId = MatrixE2eeSendPolicy.LOGIN_DEVICE_ID,
                cachedHomeserverUrl = " https://matrix.example.org/ ",
                expectedHomeserverUrl = "https://matrix.example.org",
            ),
            "cache for the fixed relay device on the same homeserver should be reused",
        )
    }

    @Test
    fun `login session cache is rejected when it belongs to an old Matrix device`() {
        assertFalse(
            MatrixE2eeSendPolicy.shouldReuseLoginSession(
                cachedDeviceId = "OLD_DEVICE",
                cachedHomeserverUrl = "https://matrix.example.org",
                expectedHomeserverUrl = "https://matrix.example.org",
            ),
            "cache belonging to an old Matrix device should be rejected",
        )
    }

    @Test
    fun `login session cache is rejected when homeserver changed`() {
        assertFalse(
            MatrixE2eeSendPolicy.shouldReuseLoginSession(
                cachedDeviceId = MatrixE2eeSendPolicy.LOGIN_DEVICE_ID,
                cachedHomeserverUrl = "https://matrix.example.org",
                expectedHomeserverUrl = "https://other.example.org",
            ),
            "cache for a different homeserver should be rejected",
        )
    }
}
