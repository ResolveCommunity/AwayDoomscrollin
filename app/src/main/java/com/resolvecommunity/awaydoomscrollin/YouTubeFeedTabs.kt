package com.resolvecommunity.awaydoomscrollin

/** The page selected in the Home feed's top tab strip. */
internal enum class YouTubeFeedTab { HOME, SUBSCRIPTIONS, OTHER, UNKNOWN }

/**
 * YouTube's 2026 layout (21.43, seen on a Galaxy S23) moved Subscriptions out
 * of the bottom bar into a tab strip (`tabs_bar_view`) under the Home toolbar:
 * Home, Subscriptions, Music, Live, More. Every page of that strip keeps the
 * bottom Home tab selected, so only the strip tells Home and Subscriptions
 * apart. Its labels are localized; its order is not.
 *
 * A channel page shows a strip with the same id (Home, Videos, Shorts, ...)
 * but always under a toolbar with Back and the channel name, even after its
 * header collapses. The feed strip has no Back: either the Home logo is above
 * it or, once a drag scrolled the logo away, it is pinned to the top.
 */
internal object YouTubeFeedTabs {
    /**
     * Whether a visible strip belongs to the Home feed rather than to a
     * channel page. [topLimit] is the lowest top a feed strip can have: just
     * below the toolbar, or below the status bar once the toolbar is gone.
     */
    fun isFeedStrip(
        stripLeft: Int,
        stripTop: Int,
        stripRight: Int,
        stripBottom: Int,
        windowLeft: Int,
        windowRight: Int,
        windowHeight: Int,
        topLimit: Int,
        backPresent: Boolean,
        selectedDestination: YouTubeNavigationDestination
    ): Boolean {
        if (backPresent) return false
        // A bottom bar that scrolled away with the feed selects nothing.
        if (selectedDestination != YouTubeNavigationDestination.HOME &&
            selectedDestination != YouTubeNavigationDestination.UNKNOWN
        ) return false
        val height = stripBottom - stripTop
        if (height <= 0 || height > windowHeight / 4 || stripTop > topLimit) return false
        // The settled strip spans the window; a strip still sliding in from
        // the side would move a Balanced curtain's top to its bottom edge.
        val slack = (windowRight - windowLeft) / 50
        return stripLeft <= windowLeft + slack && stripRight >= windowRight - slack
    }

    /**
     * The page named by the selected tab's position among the strip's tabs.
     * Nothing else tells the two pages apart: the subscribed-channels row on
     * top of Subscriptions leaves the tree once the page is scrolled, and
     * requiring it kept a scrolled Subscriptions page covered for good.
     * A five-item bottom bar still holds Subscriptions itself; a strip under
     * that bar is not this one, and its second tab stays covered.
     */
    fun tabAt(selectedIndex: Int, tabCount: Int, bottomBarItems: Int = 4): YouTubeFeedTab =
        when {
            selectedIndex !in 0 until tabCount -> YouTubeFeedTab.UNKNOWN
            selectedIndex == 0 -> YouTubeFeedTab.HOME
            selectedIndex == 1 && bottomBarItems < 5 -> YouTubeFeedTab.SUBSCRIPTIONS
            else -> YouTubeFeedTab.OTHER
        }
}
