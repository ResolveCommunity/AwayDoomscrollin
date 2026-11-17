package com.resolvecommunity.awaydoomscrollin

internal object InstagramHomeGeometry {
    fun canMeasureDuringNavigation(homeNavigationPending: Boolean, selectedHome: Boolean): Boolean =
        !homeNavigationPending || selectedHome

    /**
     * Top of the cover mounted when the Home tab is tapped, before Home has
     * drawn. Without a measurement it starts under the header, which in
     * Balanced first hides the Stories and then slides below them once Home is
     * verified. Instagram returns to Home where it was left, so the top the
     * Home curtain last had in the same window is reused: under the Stories
     * when Home was left at the top, under the header when it was scrolled,
     * from the window top when Instagram had hidden its top bar (the header
     * cover then left the feed's first rows visible for ~0.7 s, Galaxy S23).
     * Never outside the window, and never so low that the cover is thin.
     */
    /**
     * The Home curtain's top in Balanced: the bottom of the Stories tray, but
     * never lower than where the tray rests under the header. Instagram's
     * refresh (pull-down, or the Home reselect at the top) pushes the tray
     * down and back up; a curtain that followed it down lagged on the way up
     * and showed the first post's header row for ~0.5 s (Galaxy S23). Capped,
     * the curtain covers part of the moving tray instead.
     */
    fun storiesAnchor(storiesTop: Int, storiesBottom: Int, headerBottom: Int?): Int =
        if (headerBottom == null || storiesBottom <= storiesTop) storiesBottom
        else minOf(storiesBottom, headerBottom + (storiesBottom - storiesTop))

    fun tabPrecoverTop(
        windowTop: Int,
        conservativeTop: Int,
        bottom: Int,
        rememberedTop: Int?,
        minHeight: Int
    ): Int = rememberedTop?.takeIf { it >= windowTop && bottom - it >= minHeight }
        ?: conservativeTop

    val storiesTrayIds = listOf("reels_tray_container", "tray_recycler_view",
        "stories_tray", "overlay_stories_tray_container")

    fun top(windowTop: Int, storiesBottom: Int?, verifiedHeaderBottom: Int?, blockStories: Boolean): Int =
        top(windowTop, storiesBottom.takeUnless { blockStories }, verifiedHeaderBottom)

    fun top(windowTop: Int, storiesBottom: Int?, verifiedHeaderBottom: Int?): Int =
        storiesBottom?.takeIf { it > windowTop }
            ?: verifiedHeaderBottom?.takeIf { it > windowTop }
            ?: windowTop

    /**
     * Following and Favorites are secondary Home feeds. They have their own
     * action bar and no main bottom navigation, so reusing the standard Home
     * viewport leaves parts of a post exposed or strands a stale curtain in
     * the middle of an empty Favorites screen.
     */
    // Titles do not decide: the feeds are named differently in every UI
    // language, while their ids are the same everywhere.
    @Suppress("UNUSED_PARAMETER")
    fun isAlternateFeed(
        ids: Set<String>,
        titles: Set<String>,
        hasBackNavigation: Boolean
    ): Boolean {
        if (!hasBackNavigation || "action_bar_container" !in ids ||
            "refreshable_container" !in ids) return false
        if (ids.any { it in setOf("profile_header_container", "profile_viewpager",
                "direct_inbox_action_bar", "message_composer_bar", "explore_action_bar") }) return false
        val hasListShell = ids.any { it in setOf("layout_listview_parent_container",
            "swipeable_nav_view_pager_inner_recycler_view", "list") }
        if (!hasListShell) return false
        val hasFeedRows = ids.any { it.startsWith("row_feed_") }
        // An empty Following/Favorites feed shows Instagram's empty-state view
        // inside the feed list (gap binder or empty-state root, depending on
        // the build): ids, valid in every language. Search lists are excluded.
        val emptyFeed = "igds_empty_state_image" in ids &&
            ("gap_binder_group" in ids || "empty_state_view_root" in ids) &&
            ids.none { it.contains("search") }
        return hasFeedRows || emptyFeed
    }

    fun alternateFeedTop(
        windowTop: Int,
        windowBottom: Int,
        actionBarBottom: Int?,
        retainedTop: Int? = null
    ): Int =
        (actionBarBottom ?: retainedTop)?.takeIf { it > windowTop && it < windowBottom }
            ?.coerceAtMost(windowBottom - 1) ?: windowTop

    fun alternateFeedBottom(
        windowTop: Int,
        windowBottom: Int,
        contentBottom: Int?,
        retainedBottom: Int? = null
    ): Int =
        (contentBottom ?: retainedBottom)?.takeIf { it > windowTop && it <= windowBottom }
            ?: windowBottom

    fun returnedToStandardFeed(
        alternateFeedActive: Boolean,
        backVisible: Boolean,
        mainHeaderAttached: Boolean,
        storiesAttached: Boolean
    ): Boolean = alternateFeedActive && !backVisible &&
        (mainHeaderAttached || storiesAttached)
}
