package io.github.magisk317.relay.ui.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TopLevelRoutePagerSynchronizerTest {
    @Test
    fun `first top-level reconciliation snaps restored route into place`() {
        val synchronizer = TopLevelRoutePagerSynchronizer(pageCount = 5)

        assertEquals(
            TopLevelPagerSyncAction.MovePager(page = 4, snap = true),
            synchronizer.reconcile(
                routePage = 4,
                isTopLevel = true,
                settledPage = 0,
                isPagerInMotion = false,
            ),
        )
        assertEquals(
            TopLevelPagerSyncAction.None,
            synchronizer.reconcile(4, true, 4, false),
        )
    }

    @Test
    fun `route change animates pager and never publishes an intermediate page`() {
        val synchronizer = TopLevelRoutePagerSynchronizer(pageCount = 5)
        synchronizer.reconcile(0, true, 0, false)

        assertEquals(
            TopLevelPagerSyncAction.MovePager(page = 3, snap = false),
            synchronizer.reconcile(3, true, 0, false),
        )
        assertEquals(
            TopLevelPagerSyncAction.None,
            synchronizer.reconcile(3, true, 0, true),
        )
        assertEquals(
            TopLevelPagerSyncAction.None,
            synchronizer.reconcile(3, true, 3, false),
        )
    }

    @Test
    fun `cancelled route-driven animation retries its route target`() {
        val synchronizer = TopLevelRoutePagerSynchronizer(pageCount = 5)
        synchronizer.reconcile(0, true, 0, false)
        synchronizer.reconcile(4, true, 0, false)
        synchronizer.reconcile(4, true, 0, true)

        assertEquals(
            TopLevelPagerSyncAction.MovePager(page = 4, snap = false),
            synchronizer.reconcile(4, true, 2, false),
        )
    }

    @Test
    fun `settled user swipe publishes typed route once`() {
        val synchronizer = TopLevelRoutePagerSynchronizer(pageCount = 5)
        synchronizer.reconcile(1, true, 1, false)
        synchronizer.reconcile(1, true, 1, true)

        assertEquals(
            TopLevelPagerSyncAction.PublishRoute(page = 2),
            synchronizer.reconcile(1, true, 2, false),
        )
        assertEquals(
            TopLevelPagerSyncAction.None,
            synchronizer.reconcile(1, true, 2, false),
        )
        assertEquals(
            TopLevelPagerSyncAction.None,
            synchronizer.reconcile(2, true, 2, false),
        )
    }

    @Test
    fun `external route arriving during swipe wins after pager stops`() {
        val synchronizer = TopLevelRoutePagerSynchronizer(pageCount = 5)
        synchronizer.reconcile(0, true, 0, false)
        synchronizer.reconcile(0, true, 0, true)

        assertEquals(
            TopLevelPagerSyncAction.None,
            synchronizer.reconcile(4, true, 0, true),
        )
        assertEquals(
            TopLevelPagerSyncAction.MovePager(page = 4, snap = false),
            synchronizer.reconcile(4, true, 1, false),
        )
    }

    @Test
    fun `leaving top level resets next entry to a snap`() {
        val synchronizer = TopLevelRoutePagerSynchronizer(pageCount = 5)
        synchronizer.reconcile(1, true, 1, false)
        synchronizer.reconcile(1, false, 1, false)

        assertEquals(
            TopLevelPagerSyncAction.MovePager(page = 4, snap = true),
            synchronizer.reconcile(4, true, 1, false),
        )
    }

    @Test
    fun `invalid state is ignored and resets reconciliation`() {
        val synchronizer = TopLevelRoutePagerSynchronizer(pageCount = 5)
        synchronizer.reconcile(0, true, 0, false)

        assertEquals(
            TopLevelPagerSyncAction.None,
            synchronizer.reconcile(null, true, 0, false),
        )
        assertEquals(
            TopLevelPagerSyncAction.MovePager(page = 2, snap = true),
            synchronizer.reconcile(2, true, 0, false),
        )
    }

    @Test
    fun `page count must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            TopLevelRoutePagerSynchronizer(pageCount = 0)
        }
    }

    @Test
    fun `settled page exclusively owns work and benchmark tag`() {
        assertTrue(isTopLevelPageActive(page = 2, settledPage = 2))
        assertFalse(isTopLevelPageActive(page = 1, settledPage = 2))
        assertEquals(
            "records",
            benchmarkTagForPage(page = 2, settledPage = 2, tag = "records"),
        )
        assertNull(benchmarkTagForPage(page = 1, settledPage = 2, tag = "apps"))
    }

    @Test
    fun `visual page exclusively owns shared chrome`() {
        assertTrue(isTopLevelPageChromeOwner(page = 1, currentPage = 1))
        assertFalse(isTopLevelPageChromeOwner(page = 0, currentPage = 1))
    }

    @Test
    fun `top-level back stays owned until animation and route both reach overview`() {
        assertTrue(
            topLevelBackHandlerEnabled(
                isTopLevelRoute = true,
                selectedPage = 0,
                settledPage = 4,
                routePage = 4,
            ),
        )
        assertTrue(
            topLevelBackHandlerEnabled(
                isTopLevelRoute = true,
                selectedPage = 0,
                settledPage = 0,
                routePage = 4,
            ),
        )
        assertFalse(
            topLevelBackHandlerEnabled(
                isTopLevelRoute = true,
                selectedPage = 0,
                settledPage = 0,
                routePage = 0,
            ),
        )
        assertFalse(
            topLevelBackHandlerEnabled(
                isTopLevelRoute = false,
                selectedPage = 4,
                settledPage = 4,
                routePage = 4,
            ),
        )
    }
}
