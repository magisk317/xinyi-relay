package com.github.magisk317.smscode.infrastructure

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName

class TestingInfrastructureTest {

    interface SimpleService {
        fun getValue(): String
    }

    @Test
    @DisplayName("JUnit 5 and MockK should work together")
    fun testMockkIntegration() {
        // Arrange
        val service = mockk<SimpleService>()
        every { service.getValue() } returns "mocked value"

        // Act
        val result = service.getValue()

        // Assert
        assertEquals("mocked value", result)
        verify(exactly = 1) { service.getValue() }
    }
}
