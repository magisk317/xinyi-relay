package io.github.magisk317.relay.matrix.e2ee

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MatrixLoginSessionTest : FunSpec({

    test("login session cache is reused for the fixed relay device on the same homeserver") {
        MatrixE2eeRuntime.shouldReuseLoginSession(
            cachedDeviceId = MatrixE2eeRuntime.LOGIN_DEVICE_ID,
            cachedHomeserverUrl = " https://matrix.example.org/ ",
            expectedHomeserverUrl = "https://matrix.example.org",
        ) shouldBe true
    }

    test("login session cache is rejected when it belongs to an old Matrix device") {
        MatrixE2eeRuntime.shouldReuseLoginSession(
            cachedDeviceId = "OLD_DEVICE",
            cachedHomeserverUrl = "https://matrix.example.org",
            expectedHomeserverUrl = "https://matrix.example.org",
        ) shouldBe false
    }

    test("login session cache is rejected when homeserver changed") {
        MatrixE2eeRuntime.shouldReuseLoginSession(
            cachedDeviceId = MatrixE2eeRuntime.LOGIN_DEVICE_ID,
            cachedHomeserverUrl = "https://matrix.example.org",
            expectedHomeserverUrl = "https://other.example.org",
        ) shouldBe false
    }
})
