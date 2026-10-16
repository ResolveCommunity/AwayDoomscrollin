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
        fullScreenShortsVisible: Boolean
    ): YouTubeCurtainScreen {
        // Full-screen reel structure is different from a shelf embedded in a feed.
        if (fullScreenShortsVisible) return YouTubeCurtainScreen.SHORTS
        // Secondary screens can retain the underlying feed's selected bottom tab.
        // These flags must describe settled screen structure, not launch buttons.
        if (settledSearchVisible) return YouTubeCurtainScreen.SEARCH
        if (settledNotificationsVisible) return YouTubeCurtainScreen.NOTIFICATIONS
        if (otherAllowedVisible) return YouTubeCurtainScreen.OTHER_ALLOWED
        if (homeToolbarVisible) {
            return when (selectedDestination) {
                YouTubeNavigationDestination.HOME -> YouTubeCurtainScreen.HOME
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
}
