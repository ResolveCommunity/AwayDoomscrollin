package com.resolvecommunity.awaydoomscrollin

/**
 * Language-free structure of TikTok's short-video surfaces.
 *
 * TikTok obfuscates most resource ids per release and localizes every label,
 * so neither can identify a surface across languages. Its layout can: every
 * short-video feed (For You, Following, Friends, Community, Nearby and a video
 * opened from a profile, search or message) is a vertical ViewPager that fills
 * nearly the whole screen, and moving to the next video scrolls that pager.
 * Messages, profiles, search results, Shop and comment sheets scroll lists or
 * grids instead, and switching top tabs scrolls a horizontal pager.
 *
 * Measured on device (TikTok 47.4.3, French UI): a feed swipe is reported by
 * `androidx.viewpager.widget.ViewPager` filling the screen from the top with a
 * vertical scroll delta; the top-tab pager reports only a horizontal delta; the
 * profile's tab pager covers 57% of the screen; comments, messages, profile
 * grids and search results scroll RecyclerViews.
 */
internal object TikTokSurfacePolicy {
    /** Minimum share of the screen height a short-video pager covers. */
    private const val FEED_PAGER_HEIGHT_RATIO = 0.75

    /** Minimum share of the screen width a short-video pager covers. */
    private const val FEED_PAGER_WIDTH_RATIO = 0.9

    /** Top feed tabs sit in the upper part of the screen, below the status bar. */
    private const val TOP_TAB_STRIP_MAX_TOP_RATIO = 0.15

    /** The video pager starts under the status bar; search's results pager starts below its tabs. */
    private const val FEED_PAGER_MAX_TOP_RATIO = 0.05

    fun isPagerClass(className: CharSequence?): Boolean =
        className?.toString()?.contains("ViewPager") == true

    /**
     * The pager's class is reported as `androidx.viewpager.widget.ViewPager`;
     * its readable `viewpager` id is the fallback should a release rename it.
     */
    fun isPagerNode(className: CharSequence?, idFragment: String?): Boolean =
        isPagerClass(className) || idFragment == "viewpager"

    /**
     * True for a node laid out like the fullscreen short-video pager: it fills
     * the screen from the top, behind the feed tabs. The search results pager
     * ([0,374][1080,2295]) starts below its own tab strip and does not match.
     */
    fun isFeedPagerBounds(top: Int, width: Int, height: Int, screenWidth: Int, screenHeight: Int): Boolean =
        screenWidth > 0 && screenHeight > 0 && top >= 0 &&
            top <= screenHeight * FEED_PAGER_MAX_TOP_RATIO &&
            width >= screenWidth * FEED_PAGER_WIDTH_RATIO &&
            height >= screenHeight * FEED_PAGER_HEIGHT_RATIO

    /**
     * A vertical scroll of the fullscreen short-video pager is the move to the
     * next video, in any feed and in any language.
     */
    fun isFeedSwipe(
        className: CharSequence?,
        idFragment: String?,
        isVerticalScroll: Boolean,
        top: Int,
        width: Int,
        height: Int,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean = isVerticalScroll && isPagerNode(className, idFragment) &&
        isFeedPagerBounds(top, width, height, screenWidth, screenHeight)

    fun isTopTabStrip(top: Int, screenHeight: Int): Boolean =
        screenHeight > 0 && top >= 0 && top <= screenHeight * TOP_TAB_STRIP_MAX_TOP_RATIO

    /** The bottom navigation sits along the bottom edge of the screen. */
    private const val BOTTOM_BAR_MIN_TOP_RATIO = 0.85

    /**
     * TikTok's bottom navigation: one full-width row of 4-6 clickable items
     * (Home, Friends, Create, Inbox, Profile) along the bottom edge. Home is
     * the first item in every language; child order does not mirror in RTL.
     */
    fun isBottomBar(
        top: Int,
        width: Int,
        clickableChildren: Int,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean = screenWidth > 0 && screenHeight > 0 &&
        top >= screenHeight * BOTTOM_BAR_MIN_TOP_RATIO && top < screenHeight &&
        width >= screenWidth * FEED_PAGER_WIDTH_RATIO && clickableChildren in 4..6

    /**
     * For You is the default feed and the last text tab of the Home top strip
     * in every observed language and region ("Communauté, Suivis, Pour toi";
     * "Сообщество, Подписки, Рекомендации"; "Communauté, Nord, Suivis,
     * Boutique, Pour toi"). Every other top tab (Following, Community grid,
     * Nearby, Shop) is undone. The strip only counts while the bottom bar's
     * Home item is selected, which keeps search's own result tabs untouched.
     * Returns the tab index to select, or null.
     */
    fun snapBackTarget(selectedIndex: Int, tabCount: Int, homeTabSelected: Boolean): Int? {
        if (!homeTabSelected || tabCount < 2) return null
        val forYou = tabCount - 1
        return forYou.takeIf { selectedIndex in 0 until forYou }
    }
}
