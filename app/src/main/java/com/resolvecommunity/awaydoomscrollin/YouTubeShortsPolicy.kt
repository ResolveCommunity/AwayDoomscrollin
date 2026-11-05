package com.resolvecommunity.awaydoomscrollin

/**
 * Balanced-mode single-video policy: an intentionally opened Short is allowed and it
 * can be watched to the end; the first vertical swipe toward the next video requests a
 * deterministic Back action that closes the viewer on its source surface. Comment and
 * bottom-sheet scrolling stay usable. There is no time limit, quota or pattern rule - the
 * chain is what gets interrupted, never a single video.
 */
internal object YouTubeShortsPolicy {
    const val EXIT_RETRY_DELAY_MS = 250L

    /** The feed tab is never intentional single-video entry; strict also rejects links/results. */
    fun shouldBlockEntry(mode: ProtectionMode, fromShortsTab: Boolean): Boolean =
        mode == ProtectionMode.STRICT || fromShortsTab

    /**
     * The Shorts feed tab, not a Short opened from search, a link or
     * Subscriptions: a tab tap just slipped through the guard, or the bottom
     * bar has Shorts selected while a Short is on screen. Galaxy Note 10 Lite:
     * YouTube reopened on the Shorts tab it was left on, with no tap, and
     * Balanced let that feed play as if it were one chosen Short.
     */
    fun isShortsTabEntry(
        recentTabClick: Boolean,
        selected: YouTubeNavigationDestination,
        shortsVisible: Boolean
    ): Boolean = recentTabClick || (shortsVisible && selected == YouTubeNavigationDestination.SHORTS)

    /** One visible page of the Shorts pager: its adapter row and on-screen edges. */
    data class PagerRow(val row: Int, val top: Int, val bottom: Int)

    /**
     * The row that fills more than half of the Shorts pager, or null while a
     * swipe is between two Shorts. RecyclerView reports each page's adapter
     * position as its collection row, so this needs no label or delta.
     * Galaxy S9+ (Android 10): [0@0-1968] while a Short opened,
     * [0@0-1509, 1@1509-1968] mid-swipe, [0@0-94, 1@94-1968] after it.
     */
    fun dominantRow(rows: List<PagerRow>, pagerTop: Int, pagerBottom: Int): Int? {
        val pagerHeight = pagerBottom - pagerTop
        if (pagerHeight <= 0) return null
        val best = rows.filter { it.row >= 0 }.maxByOrNull { row ->
            minOf(row.bottom, pagerBottom) - maxOf(row.top, pagerTop)
        } ?: return null
        val visible = minOf(best.bottom, pagerBottom) - maxOf(best.top, pagerTop)
        return best.row.takeIf { visible * 2 > pagerHeight }
    }

    fun shouldExit(
        protectionEnabled: Boolean,
        isScrollEvent: Boolean,
        packageInForeground: Boolean,
        shortsVisible: Boolean,
        isVerticalScroll: Boolean,
        isSafeSourcePath: Boolean
    ): Boolean = protectionEnabled &&
        isScrollEvent &&
        packageInForeground &&
        shortsVisible &&
        isVerticalScroll &&
        !isSafeSourcePath
}
