package com.resolvecommunity.awaydoomscrollin

internal enum class YouTubeNavigationDestination {
    HOME,
    SHORTS,
    SUBSCRIPTIONS,
    YOU,
    UNKNOWN
}

internal enum class YouTubeProtectedSurface {
    HOME,
    SUBSCRIPTIONS
}

/** The curtain screen a navigation destination shows once settled. */
internal fun YouTubeNavigationDestination.curtainScreen(): YouTubeCurtainScreen =
    when (this) {
        YouTubeNavigationDestination.HOME -> YouTubeCurtainScreen.HOME
        YouTubeNavigationDestination.SUBSCRIPTIONS -> YouTubeCurtainScreen.SUBSCRIPTIONS
        YouTubeNavigationDestination.YOU -> YouTubeCurtainScreen.YOU
        YouTubeNavigationDestination.SHORTS -> YouTubeCurtainScreen.SHORTS
        YouTubeNavigationDestination.UNKNOWN -> YouTubeCurtainScreen.UNKNOWN
    }

internal object YouTubeSurfacePolicy {
    /**
     * Home is the recommendation feed and stays covered in both modes.
     * Subscriptions only lists channels the user chose to follow, so Balanced
     * leaves it open (its Shorts still follow the Shorts rules); Strict keeps
     * covering it.
     */
    fun subscriptionsProtected(mode: ProtectionMode): Boolean = mode == ProtectionMode.STRICT

    /**
     * A focused window the service cannot read (YouTube's runtime permission
     * prompt) is another app over YouTube. One sample can be a transition, so
     * hiding starts after two in a row; once hidden, the windows stay hidden
     * until a check finds YouTube's own window focused again.
     */
    /**
     * YouTube's own pop-ups (Premium and Music offers, menus, dialogs) open
     * in a window of their own above the main window, which stays visible
     * behind them. The curtain is drawn above every app window, so it hid
     * them and the user could neither read nor close them. A focused YouTube
     * window without the tab bar while another YouTube window is visible is
     * such a pop-up. Full-screen pages (camera, update page) hide the main
     * window and are not counted.
     */
    fun isPopUpOverMain(activeHasTabBar: Boolean, otherVisibleYouTubeWindows: Int): Boolean =
        !activeHasTabBar && otherVisibleYouTubeWindows > 0

    fun suspendForUnreadableFocus(alreadySuspended: Boolean, consecutiveChecks: Int): Boolean =
        InstagramSurfacePolicy.isUnreadableForeground(consecutiveChecks) ||
            (alreadySuspended && consecutiveChecks > 0)

    /** A loading frame with a Back arrow after Comments/watch is not evidence of Home. */
    fun preserveAllowedSecondaryDuringLoading(
        lastVerifiedScreen: YouTubeCurtainScreen,
        leadingBackVisible: Boolean
    ): Boolean = lastVerifiedScreen == YouTubeCurtainScreen.OTHER_ALLOWED && leadingBackVisible

    fun shouldRetryTabNavigation(
        requested: YouTubeNavigationDestination,
        selected: YouTubeNavigationDestination,
        visibleScreen: YouTubeCurtainScreen
    ): Boolean = selected != requested || visibleScreen in setOf(
        YouTubeCurtainScreen.SEARCH,
        YouTubeCurtainScreen.NOTIFICATIONS,
        YouTubeCurtainScreen.OTHER_ALLOWED
    )

    /**
     * The bottom navigation (`pivot_bar`) keeps one order in every language:
     * Home, Shorts, Create, Subscriptions, You. Its labels are localized
     * ("Accueil", "Abonnements : De nouveaux contenus...", "Vous"), its order
     * is not, and child order does not mirror in right-to-left layouts. A bar
     * without Create (four items) keeps the same ends.
     */
    fun navigationDestinationAt(index: Int, count: Int): YouTubeNavigationDestination = when {
        count < 4 || index !in 0 until count -> YouTubeNavigationDestination.UNKNOWN
        index == 0 -> YouTubeNavigationDestination.HOME
        index == 1 -> YouTubeNavigationDestination.SHORTS
        index == count - 1 -> YouTubeNavigationDestination.YOU
        index == count - 2 -> YouTubeNavigationDestination.SUBSCRIPTIONS
        else -> YouTubeNavigationDestination.UNKNOWN
    }

    fun strictProtectedSurface(
        destination: YouTubeNavigationDestination,
        searchSurfaceVisible: Boolean,
        shortsVisible: Boolean,
        allowedSecondarySurfaceVisible: Boolean = false
    ): YouTubeProtectedSurface? {
        // Search and allowed secondary surfaces (Notifications, History, Playlists) are explicitly allowed.
        if (searchSurfaceVisible || allowedSecondarySurfaceVisible) return null
        // The dedicated Shorts tab is not covered by the Home feed curtain.
        if (destination == YouTubeNavigationDestination.SHORTS) return null
        // Home and Subscriptions feeds are strictly protected. Embedded Shorts shelves, ads,
        // or video previews inside the feed must NEVER uncover the feed.
        if (destination == YouTubeNavigationDestination.HOME) return YouTubeProtectedSurface.HOME
        if (destination == YouTubeNavigationDestination.SUBSCRIPTIONS) return YouTubeProtectedSurface.SUBSCRIPTIONS
        if (shortsVisible) return null
        return null
    }

    fun protectedNavigationIntent(
        intent: YouTubeNavigationDestination,
        selectedDestination: YouTubeNavigationDestination
    ): YouTubeProtectedSurface? {
        // YouTube can deliver an old bottom-tab click after a newer tab is already selected.
        if (intent != selectedDestination) return null
        return when (intent) {
            YouTubeNavigationDestination.HOME -> YouTubeProtectedSurface.HOME
            YouTubeNavigationDestination.SUBSCRIPTIONS ->
                YouTubeProtectedSurface.SUBSCRIPTIONS
            else -> null
        }
    }
}
