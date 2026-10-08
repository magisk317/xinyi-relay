package io.github.magisk317.relay.sender

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SenderSigningTest {
    @Test
    fun `hmac sha256 matches known vector`() {
        assertEquals(
            "97yD9DBThCSxMpjmqm+xQ+9NWaFJRhdZl0edvC0aPNg=",
            SenderSigning.hmacSha256Base64("key", "The quick brown fox jumps over the lazy dog"),
        )
    }

    @Test
    fun `url encode uses utf8 form encoding`() {
        assertEquals("hello+%E4%B8%AD", SenderSigning.urlEncode("hello \u4e2d"))
    }
}
