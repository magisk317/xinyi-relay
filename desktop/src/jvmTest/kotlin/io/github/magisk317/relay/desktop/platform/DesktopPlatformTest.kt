package io.github.magisk317.relay.desktop.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI

class DesktopPlatformTest {

    @Test
    fun `notifier without system tray reports failure`() {
        val notifier = AwtNotifier(tray = null)
        assertFalse(notifier.notify("title", "body"))
    }

    @Test
    fun `opener forwards http and https`() {
        val opened = mutableListOf<URI>()
        val opener = AwtLinkOpener(browse = { opened += it })
        assertTrue(opener.open("https://console.example.com/devices"))
        assertTrue(opener.open("http://console.example.com"))
        assertEquals(listOf(URI("https://console.example.com/devices"), URI("http://console.example.com")), opened)
    }

    @Test
    fun `opener refuses non-web schemes without touching the OS`() {
        var calls = 0
        val opener = AwtLinkOpener(browse = { calls++ })
        for (url in listOf("javascript:alert(1)", "file:///etc/passwd", "mailto:a@b.c", "jar:file:///x!/y")) {
            assertFalse(opener.open(url), "expected refusal for $url")
        }
        assertEquals(0, calls, "browse must not be called for refused schemes")
    }

    @Test
    fun `opener treats a failed OS handoff as not opened`() {
        val opener = AwtLinkOpener(browse = { throw UnsupportedOperationException("no desktop") })
        assertFalse(opener.open("https://console.example.com"))
    }

    @Test
    fun `guard is exclusive across instances and releases on close`(@TempDir dir: File) {
        val lock = File(dir, "instance.lock")
        val first = FileLockInstanceGuard(lock)
        assertTrue(first.acquire())
        val second = FileLockInstanceGuard(lock)
        assertFalse(second.acquire(), "a live holder must block the second acquisition")
        first.close()
        assertTrue(second.acquire(), "releasing must let a waiting instance in")
        second.close()
    }

    @Test
    fun `guard creates its parent directory and re-arms after close`(@TempDir dir: File) {
        val nested = File(dir, "state/xinyi-relay/instance.lock")
        val guard = FileLockInstanceGuard(nested)
        assertTrue(guard.acquire())
        assertTrue(nested.isFile)
        guard.close()
        assertFalse(guard.acquired)
        assertTrue(guard.acquire(), "the same instance must be able to re-take the lock")
        guard.close()
    }

    @Test
    fun `guard default path lives in a private user-data directory`() {
        val lock = FileLockInstanceGuard.defaultLockFile("/home/tester")
        assertEquals("/home/tester/.xinyi-relay/instance.lock", lock.path)
    }
}
