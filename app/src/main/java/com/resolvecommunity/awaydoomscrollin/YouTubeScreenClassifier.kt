package com.resolvecommunity.awaydoomscrollin

/** Classifies one live window observation without carrying navigation history. */
internal object YouTubeScreenClassifier {
    fun classify(
        selectedDestination: YouTubeNavigationDestination,
        homeToolbarVisible: Boolean,
        verifiedYouHeaderVisible: Boolean,
        settledSearchVisible: Boolean,
        settledNotificationsVisible: Boolean,
        otherAllowedVisible: Boolean,
        fullScreenShortsVisible: Boolean,
        /**
         * The selected page of the Home feed's top tab strip, or null when no
         * feed strip is on screen (see [YouTubeFeedTabs]).
         */
        feedTab: YouTubeFeedTab? = null
    ): YouTubeCurtainScreen {
        // Full-screen reel structure is different from a shelf embedded in a feed.
        if (fullScreenShortsVisible) return YouTubeCurtainScreen.SHORTS
        // Secondary screens can retain the underlying feed's selected bottom tab.
        // These flags must describe settled screen structure, not launch buttons.
        if (settledSearchVisible) return YouTubeCurtainScreen.SEARCH
        if (settledNotificationsVisible) return YouTubeCurtainScreen.NOTIFICATIONS
        if (otherAllowedVisible) return YouTubeCurtainScreen.OTHER_ALLOWED
        // The feed strip stays pinned when a drag scrolls the logo away, and
        // a drag that starts on the bottom bar hides that bar too: the strip
        // alone still proves a feed frame. With the logo still in place a
        // hidden bottom bar means something else is on top: an expanded
        // player hides the bar while the feed under it still reports its
        // logo and strip, and calling that Home dropped the curtain on the
        // video.
        if (feedTab != null && !homeToolbarVisible &&
            selectedDestination == YouTubeNavigationDestination.UNKNOWN
        ) {
            return feedScreen(feedTab)
        }
        if (homeToolbarVisible || feedTab != null) {
            return when (selectedDestination) {
                YouTubeNavigationDestination.HOME ->
                    if (feedTab != null) feedScreen(feedTab) else YouTubeCurtainScreen.HOME
                YouTubeNavigationDestination.SUBSCRIPTIONS -> YouTubeCurtainScreen.SUBSCRIPTIONS
                else -> YouTubeCurtainScreen.UNKNOWN
            }
        }
        if (selectedDestination == YouTubeNavigationDestination.YOU && verifiedYouHeaderVisible) {
            return YouTubeCurtainScreen.YOU
        }
        // A selected tab alone may belong to the outgoing frame of an animation.
        return YouTubeCurtainScreen.UNKNOWN
    }

    /** Music, Live, More and an unsettled selection are feeds too: covered like Home. */
    private fun feedScreen(tab: YouTubeFeedTab): YouTubeCurtainScreen =
        if (tab == YouTubeFeedTab.SUBSCRIPTIONS) YouTubeCurtainScreen.SUBSCRIPTIONS
        else YouTubeCurtainScreen.HOME
}
