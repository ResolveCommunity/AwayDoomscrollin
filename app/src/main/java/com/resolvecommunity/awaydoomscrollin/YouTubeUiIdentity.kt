package com.resolvecommunity.awaydoomscrollin

/**
 * Stable, exact identities observed in YouTube's accessibility tree. Every
 * decision uses resource ids, classes and geometry; localized labels are
 * never compared, so all UI languages behave alike.
 */
internal object YouTubeUiIdentity {
    private val searchSurfaceIds = setOf(
        "search_query",
        "search_box",
        "search_edit_text",
        "search_layout"
    )

    val exactAllowedSurfaceIds: Set<String> = searchSurfaceIds + "menu_search"

    fun isSearchSurfaceId(compactId: String): Boolean = compactId in searchSurfaceIds

    /**
     * A normal watch page exposes both watch_player and watch_panel, including pre-roll ads.
     * Shared watch containers and Home's inline preview player are not watch-page evidence.
     * Callers supply visible exact-ID observations from the current window.
     */
    fun isConfirmedWatchSurface(
        watchPlayerVisible: Boolean,
        watchPanelVisible: Boolean,
        inlinePlayerVisible: Boolean
    ): Boolean = watchPlayerVisible && watchPanelVisible && !inlinePlayerVisible

    fun shouldRetryBackAfterIme(
        startedOnSearch: Boolean,
        inputMethodWasVisible: Boolean,
        searchStillVisible: Boolean
    ): Boolean = startedOnSearch && inputMethodWasVisible && searchStillVisible

    fun isAllowedSecondarySurfaceId(compactId: String): Boolean =
        compactId == "menu_search"

    /**
     * Determines whether a search surface is settled and actively in use vs animating offscreen.
     * When departing via back gesture or back button, YouTube animates the surface sliding to
     * the right. Any outward horizontal translation (right edge extending past the window or
     * left edge shifting away from its resting anchor) proves departure in progress.
     */
    fun isSettledSearchSurface(
        compactId: String,
        left: Int,
        right: Int,
        windowLeft: Int,
        windowRight: Int,
        tolerancePx: Int = 0
    ): Boolean {
        val windowWidth = windowRight - windowLeft
        if (windowWidth <= 0) return false
        if (right > windowRight + tolerancePx) return false
        val maxAllowedLeft = if (compactId == "search_layout") {
            windowLeft + tolerancePx
        } else {
            // search_box, search_edit_text, search_query: sits right of back button (~48-56dp)
            windowLeft + (windowWidth / 6) + (tolerancePx / 2)
        }
        if (left > maxAllowedLeft) return false
        val visibleWidth = minOf(right, windowRight) - maxOf(left, windowLeft)
        // Results toolbars reserve space for Back, clear, microphone and overflow. On the
        // tested 1080px YouTube window the visible search_query is only 504px wide.
        // Its exact ID and resting anchor distinguish it from a departing thin sliver.
        return visibleWidth > 0 && visibleWidth.toLong() * 3L >= windowWidth.toLong()
    }

    /**
     * During YouTube's back animation outgoing nodes can remain visible in the accessibility
     * tree even after only a thin edge of that surface remains on screen. An open search surface
     * always exposes a wide search field or results container, so require at least one third of
     * the window width before treating its IDs as authoritative.
     */
    fun hasMeaningfulHorizontalPresence(
        left: Int,
        right: Int,
        windowLeft: Int,
        windowRight: Int
    ): Boolean {
        val windowWidth = windowRight - windowLeft
        if (windowWidth <= 0) return false
        val visibleWidth = minOf(right, windowRight) - maxOf(left, windowLeft)
        return visibleWidth > 0 && visibleWidth.toLong() * 3L >= windowWidth.toLong()
    }

    fun isHorizontallyOnScreen(
        left: Int,
        right: Int,
        windowLeft: Int,
        windowRight: Int
    ): Boolean {
        if (right <= left || windowRight <= windowLeft) return false
        val center = left.toLong() + (right.toLong() - left.toLong()) / 2L
        return center >= windowLeft.toLong() && center < windowRight.toLong()
    }

    fun isHomeToolbarLogo(
        left: Int,
        right: Int,
        windowLeft: Int,
        windowRight: Int,
        minimumVisibleWidth: Int
    ): Boolean {
        if (right <= left || windowRight <= windowLeft) return false
        val visibleWidth = minOf(right, windowRight) - maxOf(left, windowLeft)
        val center = left.toLong() + (right.toLong() - left.toLong()) / 2L
        return visibleWidth >= minimumVisibleWidth &&
            center >= windowLeft &&
            center < windowLeft.toLong() + (windowRight.toLong() - windowLeft) / 2L
    }

    fun isConfirmedYouSurface(
        selectedDestination: YouTubeNavigationDestination,
        homeToolbarVisible: Boolean,
        headerLeft: Int,
        headerTop: Int,
        headerRight: Int,
        headerBottom: Int,
        windowLeft: Int,
        windowTop: Int,
        windowRight: Int,
        windowBottom: Int
    ): Boolean {
        if (selectedDestination != YouTubeNavigationDestination.YOU ||
            homeToolbarVisible || headerBottom <= headerTop || windowBottom <= windowTop
        ) return false
        return hasMeaningfulHorizontalPresence(
            headerLeft, headerRight, windowLeft, windowRight
        ) && headerBottom > windowTop &&
            headerTop < windowTop + (windowBottom - windowTop) / 2
    }

    /**
     * The title of a secondary page (Notifications, History, a playlist,
     * Comments...) is the toolbar's non-clickable TextView next to Back. Home
     * and Subscriptions show the logo there instead. Only the shape is read,
     * never the localized words, and callers pass nodes from `toolbar` only.
     */
    fun isToolbarTitle(
        className: CharSequence?,
        text: CharSequence?,
        isClickable: Boolean,
        top: Int,
        windowHeight: Int,
        left: Int,
        right: Int,
        windowLeft: Int,
        windowRight: Int
    ): Boolean {
        if (isClickable || text.isNullOrBlank() || className?.contains("TextView") != true ||
            windowHeight <= 0 || top >= windowHeight / 4 ||
            !isHorizontallyOnScreen(left, right, windowLeft, windowRight)
        ) {
            return false
        }
        val windowWidth = windowRight - windowLeft
        return windowWidth <= 0 ||
            (left <= windowLeft + windowWidth / 3 && right <= windowRight + windowWidth / 20)
    }

    /** A title at its resting anchor right of Back, not one sliding in or out. */
    fun isSettledToolbarTitle(
        className: CharSequence?,
        text: CharSequence?,
        isClickable: Boolean,
        top: Int,
        windowHeight: Int,
        left: Int,
        right: Int,
        windowLeft: Int,
        windowRight: Int,
        tolerancePx: Int = 0
    ): Boolean {
        if (!isToolbarTitle(className, text, isClickable, top, windowHeight,
                left, right, windowLeft, windowRight)
        ) {
            return false
        }
        val windowWidth = windowRight - windowLeft
        if (windowWidth <= 0) return false
        if (right > windowRight + tolerancePx) return false
        return left <= windowLeft + (windowWidth / 5) + (tolerancePx / 2)
    }

    /**
     * Back ("Navigate up" in any language) is the toolbar's clickable
     * ImageButton at the leading edge: [0,98][168,242] on a 1080px window, on
     * Search, Notifications and every other secondary page.
     */
    fun isLeadingToolbarButton(
        className: CharSequence?,
        isClickable: Boolean,
        left: Int,
        right: Int,
        windowLeft: Int,
        windowRight: Int,
        tolerancePx: Int = 0
    ): Boolean {
        if (!isClickable || className?.contains("ImageButton") != true || right <= left) return false
        val windowWidth = windowRight - windowLeft
        if (windowWidth <= 0) return false
        if (right > windowRight + tolerancePx) return false
        return left <= windowLeft + tolerancePx && right - left <= windowWidth / 5
    }

    /**
     * Toolbar launch buttons of the Home and Subscriptions feeds, by their
     * `menu_item_N` position: Search is the last item and Notifications the
     * one before it (an optional Cast button comes first). The You tab adds
     * Settings after Search, so this is only valid on the two feed toolbars.
     */
    fun feedToolbarLaunchScreen(itemIndex: Int, lastItemIndex: Int): YouTubeCurtainScreen? = when {
        itemIndex < 0 || lastItemIndex < 0 -> null
        itemIndex == lastItemIndex -> YouTubeCurtainScreen.SEARCH
        itemIndex == lastItemIndex - 1 -> YouTubeCurtainScreen.NOTIFICATIONS
        else -> null
    }

    /** The N of a `menu_item_N` toolbar slot, or -1. */
    fun menuItemIndex(compactId: String): Int =
        compactId.removePrefix("menu_item_").takeIf { it != compactId }?.toIntOrNull() ?: -1

    /**
     * A titled secondary page is Notifications when it was reached from a
     * feed toolbar or stays within the Notifications visit; reached from the
     * You tab (History, Playlists) it is another allowed page. Channel and
     * watch pages are identified by their own ids before this applies.
     */
    fun isNotificationsPage(
        titledPage: Boolean,
        structurallyOtherAllowed: Boolean,
        notificationsRequested: Boolean,
        lastVerifiedScreen: YouTubeCurtainScreen,
        withinNotificationsVisit: Boolean
    ): Boolean = titledPage && !structurallyOtherAllowed && (
        notificationsRequested || withinNotificationsVisit ||
            lastVerifiedScreen == YouTubeCurtainScreen.HOME ||
            lastVerifiedScreen == YouTubeCurtainScreen.SUBSCRIPTIONS
        )
}
