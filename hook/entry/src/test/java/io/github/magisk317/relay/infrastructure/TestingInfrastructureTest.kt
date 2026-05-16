package io.github.magisk317.relay.infrastructure

import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.answering.returns
import dev.mokkery.verify.VerifyMode.Companion.exactly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName

class TestingInfrastructureTest {

    interface SimpleService {
        fun getValue(): String
    }

    @Test
    @DisplayName("JUnit 5 and Mokkery should work together")
    fun testMokkeryIntegration() {
        val service = mock<SimpleService>()
        every { service.getValue() } returns "mocked value"

        val result = service.getValue()

        assertEquals("mocked value", result)
        verify(exactly(1)) { service.getValue() }
    }
}
