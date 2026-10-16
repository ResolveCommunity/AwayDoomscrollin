package com.resolvecommunity.awaydoomscrollin

import android.graphics.Rect

/**
 * Manages deterministic viewport geometry calculations for YouTube protected feeds
 * (Home and Subscriptions). Prevents collapsing headers on secondary surfaces (such as
 * the "You" tab profile header) from introducing gaps that expose the top of the feed
 * or Shorts shelves on navigation return.
 */
internal object YouTubeFeedGeometry {

    /**
     * Calculates the top coordinate of the YouTube protected feed viewport.
     */
    fun calculateTop(
        windowTop: Int,
        statusBarInset: Int,
        toolbarBottom: Int?,
        toolbarContainerBottom: Int?,
        dp56: Int,
        dp16: Int,
        landscape: Boolean = false
    ): Int {
        // In landscape YouTube's subscriptions layout is a two-pane feed. The
        // portrait status-bar inset is not a usable boundary there, so use the
        // live toolbar edge when it is available. This keeps the notification
        // and search controls usable while the feed below remains protected.
        if (landscape) {
            val maxToolbarBottom = windowTop + dp56.coerceAtLeast(0) * 2 + dp16.coerceAtLeast(0)
            return listOf(toolbarContainerBottom, toolbarBottom)
                .firstOrNull { it != null && it > windowTop && it <= maxToolbarBottom }
                ?: windowTop
        }
        // Insets and node bounds are screen coordinates. A root starting inside the status
        // bar must add only the remaining inset, not the entire inset a second time.
        val contentTop = maxOf(windowTop, statusBarInset.coerceAtLeast(0))
        val stableToolbarBottom = contentTop + dp56.coerceAtLeast(0)
        val maxAllowedTop = stableToolbarBottom + dp16.coerceAtLeast(0)
        fun verifiedToolbarBottom(candidate: Int?): Int? = candidate?.takeIf {
            it > contentTop && it <= maxAllowedTop
        }
        val candidateToolbar = verifiedToolbarBottom(toolbarBottom)
            ?: verifiedToolbarBottom(toolbarContainerBottom)

        // A stale account header is rejected, rather than clamped to a position below the
        // feed toolbar. Even an accepted tolerance must never expose a strip of the feed.
        return (candidateToolbar ?: stableToolbarBottom).coerceAtMost(stableToolbarBottom)
    }

    /**
     * Calculates the bottom coordinate of the YouTube protected feed viewport.
     */
    fun calculateBottom(
        windowTop: Int,
        windowBottom: Int,
        bottomBarTop: Int?,
        dp56: Int,
        dp48: Int
    ): Int {
        if (windowBottom <= windowTop) return windowTop
        val minimumBarTop = windowTop + dp56.coerceAtLeast(0) * 2
        val verifiedBarTop = bottomBarTop?.takeIf {
            it >= minimumBarTop && it < windowBottom
        }
        return verifiedBarTop ?: (windowBottom - dp48.coerceAtLeast(0))
            .coerceIn(windowTop, windowBottom)
    }

    /**
     * Calculates the viewport bounds for a YouTube protected feed.
     */
    fun calculateFeedViewport(
        rootBounds: Rect,
        statusBarInset: Int,
        toolbarBottom: Int?,
        toolbarContainerBottom: Int?,
        bottomBarTop: Int?,
        dp56: Int,
        dp48: Int,
        dp16: Int
    ): Rect? {
        if (rootBounds.width() <= 0 || rootBounds.height() <= 0) return null

        val top = calculateTop(
            windowTop = rootBounds.top,
            statusBarInset = statusBarInset,
            toolbarBottom = toolbarBottom,
            toolbarContainerBottom = toolbarContainerBottom,
            dp56 = dp56,
            dp16 = dp16,
            landscape = rootBounds.width() > rootBounds.height()
        ).coerceIn(rootBounds.top, rootBounds.bottom)

        val bottom = calculateBottom(
            windowTop = rootBounds.top,
            windowBottom = rootBounds.bottom,
            bottomBarTop = bottomBarTop,
            dp56 = dp56,
            dp48 = dp48
        ).coerceIn(rootBounds.top, rootBounds.bottom)

        return if (bottom > top) Rect(rootBounds.left, top, rootBounds.right, bottom) else null
    }

    /**
     * The minimized player's rectangle, or null when the observed controls do
     * not look like a minimized player (a full-screen or expanded watch page
     * must never punch a hole into the feed curtain).
     */
    fun miniplayerCutout(controls: Rect?, timeBar: Rect? = null, window: Rect): Rect? {
        // The controls rectangle is the player itself. The time bar container
        // extends below its rounded corners, so it is only a fallback.
        val cutout = listOfNotNull(controls, timeBar)
            .firstOrNull { it.width() > 0 && it.height() > 0 }
            ?.let(::Rect) ?: return null
        if (window.width() <= 0 || window.height() <= 0) return null
        if (!cutout.intersect(window)) return null
        val area = cutout.width().toLong() * cutout.height()
        val windowArea = window.width().toLong() * window.height()
        // Minimized players are small; an expanded player is never a cutout.
        if (area * 4 > windowArea) return null
        return cutout
    }

    /**
     * Splits the feed curtain around a minimized player docked at the bottom
     * or the top edge (the user can drag it to either). The first rectangle is
     * the main curtain; the others fill the strips beside and between the
     * player and the curtain edge so no feed pixels remain uncovered. A player
     * floating in the middle of the feed leaves the curtain whole: covering
     * protection wins over convenience.
     */
    fun curtainSegments(curtain: Rect, cutout: Rect?, tolerancePx: Int = 0): List<Rect> {
        if (cutout == null || !Rect.intersects(curtain, cutout)) return listOf(Rect(curtain))
        val left = maxOf(cutout.left, curtain.left)
        val right = minOf(cutout.right, curtain.right)
        val bottomDocked = cutout.bottom >= curtain.bottom - tolerancePx && cutout.top > curtain.top
        val topDocked = cutout.top <= curtain.top + tolerancePx && cutout.bottom < curtain.bottom
        val segments = mutableListOf<Rect>()
        when {
            bottomDocked -> {
                segments += Rect(curtain.left, curtain.top, curtain.right, cutout.top)
                if (left > curtain.left) segments += Rect(curtain.left, cutout.top, left, curtain.bottom)
                if (right < curtain.right) segments += Rect(right, cutout.top, curtain.right, curtain.bottom)
                if (cutout.bottom < curtain.bottom) {
                    segments += Rect(left, cutout.bottom, right, curtain.bottom)
                }
            }
            topDocked -> {
                segments += Rect(curtain.left, cutout.bottom, curtain.right, curtain.bottom)
                if (left > curtain.left) segments += Rect(curtain.left, curtain.top, left, cutout.bottom)
                if (right < curtain.right) segments += Rect(right, curtain.top, curtain.right, cutout.bottom)
                if (cutout.top > curtain.top) segments += Rect(left, curtain.top, right, cutout.top)
            }
            else -> segments += Rect(curtain)
        }
        return segments
    }

    /**
     * Determines whether the given destination is authoritative to update cached return bounds.
     * Only Home and Subscriptions feeds have verified feed viewport geometry.
     * Secondary surfaces like You, Search, or Notifications must never overwrite return bounds.
     */
    fun shouldUpdateReturnBounds(destination: YouTubeNavigationDestination): Boolean =
        destination == YouTubeNavigationDestination.HOME ||
            destination == YouTubeNavigationDestination.SUBSCRIPTIONS
}
