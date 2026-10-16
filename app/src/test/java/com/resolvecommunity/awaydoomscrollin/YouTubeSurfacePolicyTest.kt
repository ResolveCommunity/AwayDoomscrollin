package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeSurfacePolicyTest {
    @Test
    fun `comments return through unknown frames to Notifications without a feed flash`() {
        val curtain = YouTubeCurtainState()
        curtain.observe(YouTubeCurtainScreen.HOME, 0)
        curtain.observe(YouTubeCurtainScreen.NOTIFICATIONS, 10)
        curtain.observe(YouTubeCurtainScreen.OTHER_ALLOWED, 20)
        assertTrue(YouTubeSurfacePolicy.preserveAllowedSecondaryDuringLoading(
            YouTubeCurtainScreen.OTHER_ALLOWED, true))
        assertNull(curtain.observe(YouTubeCurtainScreen.UNKNOWN, 30))
        assertNull(curtain.observe(YouTubeCurtainScreen.UNKNOWN, 40))
        assertNull(curtain.observe(YouTubeCurtainScreen.NOTIFICATIONS, 50))
        curtain.request(YouTubeCurtainScreen.HOME, 60)
        assertEquals(YouTubeProtectedSurface.HOME, curtain.observe(YouTubeCurtainScreen.UNKNOWN, 70))
    }

    @Test
    fun `missing back arrow or direct Notifications return never suppresses feed protection`() {
        assertFalse(YouTubeSurfacePolicy.preserveAllowedSecondaryDuringLoading(
            YouTubeCurtainScreen.OTHER_ALLOWED, false))
        assertFalse(YouTubeSurfacePolicy.preserveAllowedSecondaryDuringLoading(
            YouTubeCurtainScreen.NOTIFICATIONS, true))
        assertFalse(YouTubeSurfacePolicy.preserveAllowedSecondaryDuringLoading(
            YouTubeCurtainScreen.HOME, true))
    }
    @Test
    fun `selected tab behind search is not proof of completed tab navigation`() {
        for (destination in listOf(YouTubeNavigationDestination.HOME,
                YouTubeNavigationDestination.SUBSCRIPTIONS, YouTubeNavigationDestination.YOU)) {
            assertTrue(YouTubeSurfacePolicy.shouldRetryTabNavigation(
                destination, destination, YouTubeCurtainScreen.SEARCH))
        }
    }

    @Test
    fun `accepted tab animation and verified target do not enqueue duplicate taps`() {
        assertFalse(YouTubeSurfacePolicy.shouldRetryTabNavigation(
            YouTubeNavigationDestination.HOME, YouTubeNavigationDestination.HOME, YouTubeCurtainScreen.UNKNOWN))
        assertFalse(YouTubeSurfacePolicy.shouldRetryTabNavigation(
            YouTubeNavigationDestination.YOU, YouTubeNavigationDestination.YOU, YouTubeCurtainScreen.YOU))
        assertTrue(YouTubeSurfacePolicy.shouldRetryTabNavigation(
            YouTubeNavigationDestination.YOU, YouTubeNavigationDestination.HOME, YouTubeCurtainScreen.HOME))
    }
    @Test
    fun `search results override selected Home destination`() {
        assertNull(
            YouTubeSurfacePolicy.strictProtectedSurface(
                destination = YouTubeNavigationDestination.HOME,
                searchSurfaceVisible = true,
                shortsVisible = false
            )
        )
    }

    @Test
    fun `Home feed is protected outside search`() {
        assertEquals(
            YouTubeProtectedSurface.HOME,
            YouTubeSurfacePolicy.strictProtectedSurface(
                destination = YouTubeNavigationDestination.HOME,
                searchSurfaceVisible = false,
                shortsVisible = false
            )
        )
    }

    @Test
    fun `Home feed with embedded Shorts shelf or ad remains protected`() {
        assertEquals(
            YouTubeProtectedSurface.HOME,
            YouTubeSurfacePolicy.strictProtectedSurface(
                destination = YouTubeNavigationDestination.HOME,
                searchSurfaceVisible = false,
                shortsVisible = true
            )
        )
    }

    @Test
    fun `Subscriptions feed with embedded Shorts shelf remains protected`() {
        assertEquals(
            YouTubeProtectedSurface.SUBSCRIPTIONS,
            YouTubeSurfacePolicy.strictProtectedSurface(
                destination = YouTubeNavigationDestination.SUBSCRIPTIONS,
                searchSurfaceVisible = false,
                shortsVisible = true
            )
        )
    }

    @Test
    fun `dedicated Shorts tab is not treated as protected feed`() {
        assertNull(
            YouTubeSurfacePolicy.strictProtectedSurface(
                destination = YouTubeNavigationDestination.SHORTS,
                searchSurfaceVisible = false,
                shortsVisible = true
            )
        )
    }

    @Test
    fun `secondary toolbar surface overrides selected Home destination`() {
        assertNull(
            YouTubeSurfacePolicy.strictProtectedSurface(
                destination = YouTubeNavigationDestination.HOME,
                searchSurfaceVisible = false,
                shortsVisible = false,
                allowedSecondarySurfaceVisible = true
            )
        )
    }

    @Test
    fun `Subscriptions feed is protected`() {
        assertEquals(
            YouTubeProtectedSurface.SUBSCRIPTIONS,
            YouTubeSurfacePolicy.strictProtectedSurface(
                destination = YouTubeNavigationDestination.SUBSCRIPTIONS,
                searchSurfaceVisible = false,
                shortsVisible = false
            )
        )
    }

    @Test
    fun `Subscriptions badge label is recognized`() {
        assertEquals(
            YouTubeNavigationDestination.SUBSCRIPTIONS,
            YouTubeSurfacePolicy.navigationDestination(
                "Subscriptions: New content is available"
            )
        )
    }

    @Test
    fun `You destination stays allowed`() {
        assertNull(
            YouTubeSurfacePolicy.strictProtectedSurface(
                destination = YouTubeNavigationDestination.YOU,
                searchSurfaceVisible = false,
                shortsVisible = false
            )
        )
    }

    @Test
    fun `delayed Subscriptions click cannot overwrite selected Home`() {
        assertNull(
            YouTubeSurfacePolicy.protectedNavigationIntent(
                intent = YouTubeNavigationDestination.SUBSCRIPTIONS,
                selectedDestination = YouTubeNavigationDestination.HOME
            )
        )
    }

    @Test
    fun `matching protected click is accepted`() {
        assertEquals(
            YouTubeProtectedSurface.HOME,
            YouTubeSurfacePolicy.protectedNavigationIntent(
                intent = YouTubeNavigationDestination.HOME,
                selectedDestination = YouTubeNavigationDestination.HOME
            )
        )
    }
}
