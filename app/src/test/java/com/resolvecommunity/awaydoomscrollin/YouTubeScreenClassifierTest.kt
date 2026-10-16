package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeScreenClassifierTest {
    @Test
    fun `notification comment page with selected Home is not a feed`() {
        assertEquals(YouTubeCurtainScreen.OTHER_ALLOWED, YouTubeScreenClassifier.classify(
            YouTubeNavigationDestination.HOME, false, false, false, false, true, false))
        val curtain = YouTubeCurtainState()
        curtain.observe(YouTubeCurtainScreen.HOME, 0)
        assertEquals(null, curtain.observe(YouTubeCurtainScreen.OTHER_ALLOWED, 10))
        assertEquals(null, curtain.observe(YouTubeCurtainScreen.NOTIFICATIONS, 20))
        assertEquals(YouTubeProtectedSurface.HOME, curtain.observe(YouTubeCurtainScreen.HOME, 30))
    }
    @Test
    fun `Home requires its live toolbar and selected tab`() {
        assertEquals(YouTubeCurtainScreen.HOME, classify(YouTubeNavigationDestination.HOME, home = true))
        assertEquals(YouTubeCurtainScreen.UNKNOWN, classify(YouTubeNavigationDestination.HOME))
    }

    @Test
    fun `Subscriptions requires its live toolbar and selected tab`() {
        assertEquals(YouTubeCurtainScreen.SUBSCRIPTIONS, classify(YouTubeNavigationDestination.SUBSCRIPTIONS, home = true))
        assertEquals(YouTubeCurtainScreen.UNKNOWN, classify(YouTubeNavigationDestination.SUBSCRIPTIONS))
    }

    @Test
    fun `stale selected You with Home toolbar is ambiguous`() {
        assertEquals(YouTubeCurtainScreen.UNKNOWN, classify(YouTubeNavigationDestination.YOU, home = true, profile = true))
    }

    @Test
    fun `selected You without actual profile header cannot release protection`() {
        assertEquals(YouTubeCurtainScreen.UNKNOWN, classify(YouTubeNavigationDestination.YOU))
    }

    @Test
    fun `verified account page is allowed`() {
        assertEquals(YouTubeCurtainScreen.YOU, classify(YouTubeNavigationDestination.YOU, profile = true))
    }

    @Test
    fun `outgoing profile header cannot override selected Home`() {
        assertEquals(YouTubeCurtainScreen.HOME, classify(YouTubeNavigationDestination.HOME, home = true, profile = true))
        assertEquals(YouTubeCurtainScreen.UNKNOWN, classify(YouTubeNavigationDestination.HOME, profile = true))
    }

    @Test
    fun `settled Search is recognized with stale selected Home`() {
        assertEquals(YouTubeCurtainScreen.SEARCH, classify(YouTubeNavigationDestination.HOME, search = true))
    }

    @Test
    fun `settled Notifications is recognized with stale selected Subscriptions`() {
        assertEquals(YouTubeCurtainScreen.NOTIFICATIONS, classify(YouTubeNavigationDestination.SUBSCRIPTIONS, notifications = true))
    }

    @Test
    fun `settled allowed screen identity wins over retained feed toolbar`() {
        assertEquals(YouTubeCurtainScreen.SEARCH, classify(YouTubeNavigationDestination.HOME, home = true, search = true))
        assertEquals(YouTubeCurtainScreen.NOTIFICATIONS, classify(YouTubeNavigationDestination.HOME, home = true, notifications = true))
    }

    @Test
    fun `other verified allowed screen is classified independently of underlying tab`() {
        assertEquals(YouTubeCurtainScreen.OTHER_ALLOWED, classify(YouTubeNavigationDestination.HOME, otherAllowed = true))
    }

    @Test
    fun `full screen Shorts is recognized independently of underlying selected tab`() {
        for (destination in YouTubeNavigationDestination.values()) {
            assertEquals(YouTubeCurtainScreen.SHORTS, classify(destination, shorts = true))
        }
    }

    @Test
    fun `selected Shorts alone does not prove full screen playback`() {
        assertEquals(YouTubeCurtainScreen.UNKNOWN, classify(YouTubeNavigationDestination.SHORTS))
    }

    @Test
    fun `ambiguous or empty tree never defaults to Home`() {
        assertEquals(YouTubeCurtainScreen.UNKNOWN, classify(YouTubeNavigationDestination.UNKNOWN))
        assertEquals(YouTubeCurtainScreen.UNKNOWN, classify(YouTubeNavigationDestination.UNKNOWN, home = true))
        assertEquals(YouTubeCurtainScreen.UNKNOWN, classify(YouTubeNavigationDestination.UNKNOWN, profile = true))
    }

    private fun classify(
        destination: YouTubeNavigationDestination,
        home: Boolean = false,
        profile: Boolean = false,
        search: Boolean = false,
        notifications: Boolean = false,
        otherAllowed: Boolean = false,
        shorts: Boolean = false
    ): YouTubeCurtainScreen = YouTubeScreenClassifier.classify(
        selectedDestination = destination,
        homeToolbarVisible = home,
        verifiedYouHeaderVisible = profile,
        settledSearchVisible = search,
        settledNotificationsVisible = notifications,
        otherAllowedVisible = otherAllowed,
        fullScreenShortsVisible = shorts
    )
}
