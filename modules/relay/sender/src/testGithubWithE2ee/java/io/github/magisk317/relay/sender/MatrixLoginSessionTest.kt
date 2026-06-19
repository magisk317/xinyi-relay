package io.github.magisk317.relay.sender

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MatrixLoginSessionTest : FunSpec({

    test("login session cache is reused for the fixed relay device on the same homeserver") {
        MatrixE2eeUtils.shouldReuseLoginSession(
            cachedDeviceId = MatrixE2eeUtils.LOGIN_DEVICE_ID,
            cachedHomeserverUrl = " https://matrix.example.org/ ",
            expectedHomeserverUrl = "https://matrix.example.org",
        ) shouldBe true
    }

    test("login session cache is rejected when it belongs to an old Matrix device") {
        MatrixE2eeUtils.shouldReuseLoginSession(
            cachedDeviceId = "OLD_DEVICE",
            cachedHomeserverUrl = "https://matrix.example.org",
            expectedHomeserverUrl = "https://matrix.example.org",
        ) shouldBe false
    }

    test("login session cache is rejected when homeserver changed") {
        MatrixE2eeUtils.shouldReuseLoginSession(
            cachedDeviceId = MatrixE2eeUtils.LOGIN_DEVICE_ID,
            cachedHomeserverUrl = "https://matrix.example.org",
            expectedHomeserverUrl = "https://other.example.org",
        ) shouldBe false
    }
})
