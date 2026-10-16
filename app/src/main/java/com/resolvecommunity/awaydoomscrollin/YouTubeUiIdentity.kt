package com.resolvecommunity.awaydoomscrollin

import java.util.Locale

/** Stable, exact identities observed in YouTube's accessibility tree. */
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

    fun isAllowedSecondaryToolbarTitle(
        rawText: String?,
        isClickable: Boolean,
        top: Int,
        windowHeight: Int,
        left: Int,
        right: Int,
        windowLeft: Int,
        windowRight: Int
    ): Boolean {
        if (isClickable || windowHeight <= 0 || top >= windowHeight / 4 ||
            !isHorizontallyOnScreen(left, right, windowLeft, windowRight)
        ) {
            return false
        }
        val windowWidth = windowRight - windowLeft
        if (windowWidth > 0 && (left > windowLeft + windowWidth / 3 || right > windowRight + (windowWidth / 20))) {
            return false
        }
        val text = rawText.orEmpty().trim().lowercase(Locale.ROOT)
        return text.startsWith("notifications") ||
            text.startsWith("bildirim") ||
            text == "history" || text == "geçmiş" || text == "gecmis" ||
            text == "playlists" || text == "oynatma listeleri" ||
            text == "comments" || text == "yorumlar" ||
            text == "replies" || text == "yanıtlar" || text == "yanitlar" ||
            text == "library" || text == "kitaplık" || text == "kitaplik"
    }

    fun isSettledSecondaryToolbarTitle(
        rawText: String?,
        isClickable: Boolean,
        top: Int,
        windowHeight: Int,
        left: Int,
        right: Int,
        windowLeft: Int,
        windowRight: Int,
        tolerancePx: Int = 0
    ): Boolean {
        if (!isAllowedSecondaryToolbarTitle(
                rawText = rawText,
                isClickable = isClickable,
                top = top,
                windowHeight = windowHeight,
                left = left,
                right = right,
                windowLeft = windowLeft,
                windowRight = windowRight
            )
        ) {
            return false
        }
        val windowWidth = windowRight - windowLeft
        if (windowWidth <= 0) return false
        if (right > windowRight + tolerancePx) return false
        val maxAllowedLeft = windowLeft + (windowWidth / 5) + (tolerancePx / 2)
        return left <= maxAllowedLeft
    }

    fun isSettledLeadingBackControl(
        rawDescription: String?,
        rawText: String?,
        left: Int,
        right: Int,
        windowLeft: Int,
        windowRight: Int,
        tolerancePx: Int = 0
    ): Boolean {
        if (!isBackNavigationLabel(rawDescription, rawText)) return false
        val windowWidth = windowRight - windowLeft
        if (windowWidth <= 0) return false
        if (right > windowRight + tolerancePx) return false
        return left <= windowLeft + tolerancePx
    }

    fun isExplicitlyAllowedLaunch(compactId: String, rawLabel: String?): Boolean {
        if (compactId in exactAllowedSurfaceIds) return true
        val label = rawLabel.orEmpty().trim().lowercase(Locale.ROOT)
        return label.startsWith("search") || label.startsWith("ara") ||
            label.startsWith("notifications") || label.startsWith("bildirim") ||
            label == "youtube'da ara"
    }

    fun isBackNavigationLabel(rawDescription: String?, rawText: String?): Boolean {
        val label = (rawDescription.takeIf { !it.isNullOrBlank() } ?: rawText).orEmpty().trim().lowercase(Locale.ROOT)
        return label == "navigate up" || label == "back" || label == "geri" ||
            label == "yukarı git" || label == "yukari git" || label == "kapat" || label == "close"
    }
}
