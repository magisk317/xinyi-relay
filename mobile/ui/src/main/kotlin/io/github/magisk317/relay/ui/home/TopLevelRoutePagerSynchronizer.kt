package io.github.magisk317.relay.ui.home

internal sealed interface TopLevelPagerSyncAction {
    data object None : TopLevelPagerSyncAction

    data class MovePager(
        val page: Int,
        val snap: Boolean,
    ) : TopLevelPagerSyncAction

    data class PublishRoute(val page: Int) : TopLevelPagerSyncAction
}

/**
 * Reconciles the typed top-level route with the pager without letting either side continuously
 * overwrite the other. Route changes drive a pager move; a completed user swipe publishes the
 * newly settled page back to navigation.
 */
internal class TopLevelRoutePagerSynchronizer(
    private val pageCount: Int,
) {
    private var wasTopLevel = false
    private var lastRoutePage: Int? = null
    private var routeDrivenTarget: Int? = null
    private var routeDrivenMoveShouldSnap = false
    private var pendingPublishedPage: Int? = null

    init {
        require(pageCount > 0) { "pageCount must be positive" }
    }

    @Suppress("ReturnCount") // Explicit state-machine exits keep route/pager ownership readable.
    fun reconcile(
        routePage: Int?,
        isTopLevel: Boolean,
        settledPage: Int,
        isPagerInMotion: Boolean,
    ): TopLevelPagerSyncAction {
        if (!isTopLevel || !routePage.isValidPage() || !settledPage.isValidPage()) {
            reset()
            return TopLevelPagerSyncAction.None
        }

        if (!wasTopLevel) {
            wasTopLevel = true
            lastRoutePage = routePage
            if (routePage == settledPage) return TopLevelPagerSyncAction.None

            routeDrivenTarget = routePage
            routeDrivenMoveShouldSnap = true
            return moveRouteTargetWhenIdle(isPagerInMotion)
        }

        val routeChanged = lastRoutePage != routePage
        lastRoutePage = routePage

        pendingPublishedPage?.let { publishedPage ->
            when {
                routePage == publishedPage -> {
                    pendingPublishedPage = null
                    return TopLevelPagerSyncAction.None
                }

                routeChanged -> pendingPublishedPage = null
                isPagerInMotion -> {
                    pendingPublishedPage = null
                    return TopLevelPagerSyncAction.None
                }

                settledPage == publishedPage -> return TopLevelPagerSyncAction.None
            }
        }

        if (routeChanged) {
            routeDrivenTarget = routePage
            routeDrivenMoveShouldSnap = false
            if (routePage == settledPage) {
                clearRouteDrivenMove()
                return TopLevelPagerSyncAction.None
            }
            return moveRouteTargetWhenIdle(isPagerInMotion)
        }

        if (isPagerInMotion) {
            return TopLevelPagerSyncAction.None
        }

        routeDrivenTarget?.let { targetPage ->
            if (settledPage == targetPage) {
                clearRouteDrivenMove()
                return TopLevelPagerSyncAction.None
            }
            return TopLevelPagerSyncAction.MovePager(
                page = targetPage,
                snap = routeDrivenMoveShouldSnap,
            )
        }

        if (routePage != settledPage) {
            pendingPublishedPage = settledPage
            return TopLevelPagerSyncAction.PublishRoute(settledPage)
        }

        return TopLevelPagerSyncAction.None
    }

    private fun moveRouteTargetWhenIdle(isPagerInMotion: Boolean): TopLevelPagerSyncAction {
        val targetPage = routeDrivenTarget ?: return TopLevelPagerSyncAction.None
        return if (isPagerInMotion) {
            TopLevelPagerSyncAction.None
        } else {
            TopLevelPagerSyncAction.MovePager(
                page = targetPage,
                snap = routeDrivenMoveShouldSnap,
            )
        }
    }

    private fun clearRouteDrivenMove() {
        routeDrivenTarget = null
        routeDrivenMoveShouldSnap = false
    }

    private fun reset() {
        wasTopLevel = false
        lastRoutePage = null
        clearRouteDrivenMove()
        pendingPublishedPage = null
    }

    private fun Int?.isValidPage(): Boolean = this != null && this in 0 until pageCount
}

internal fun isTopLevelPageActive(
    page: Int,
    settledPage: Int,
): Boolean = page == settledPage

internal fun isTopLevelPageChromeOwner(
    page: Int,
    currentPage: Int,
): Boolean = page == currentPage

internal fun benchmarkTagForPage(
    page: Int,
    settledPage: Int,
    tag: String,
): String? = tag.takeIf { isTopLevelPageActive(page, settledPage) }

/** Keeps top-level back owned until intent, visuals, and the typed route all reach Overview. */
internal fun topLevelBackHandlerEnabled(
    isTopLevelRoute: Boolean,
    selectedPage: Int,
    settledPage: Int,
    routePage: Int,
): Boolean = isTopLevelRoute && (selectedPage != 0 || settledPage != 0 || routePage != 0)
