package com.resolvecommunity.awaydoomscrollin

internal object InstagramHomeGeometry {
    private val alternateFeedTitles = setOf(
        "following", "favorites",
        "takip", "takip edilenler", "favoriler",
        "abonnements", "favoris",
        "gefolgt", "favoriten",
        "siguiendo", "favoritos",
        "seguindo", "seguiti", "preferiti"
    )

    fun canMeasureDuringNavigation(homeNavigationPending: Boolean, selectedHome: Boolean): Boolean =
        !homeNavigationPending || selectedHome

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
        val hasSemanticTitle = titles.any { raw ->
            val title = raw.trim().lowercase(java.util.Locale.ROOT)
            title in alternateFeedTitles ||
                alternateFeedTitles.any { token -> title.contains("$token feed") }
        }
        return hasFeedRows || hasSemanticTitle
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
