package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeCurtainStateTest {
    @Test
    fun `You tab restored results release the curtain after a confirmed streak`() {
        val state = YouTubeCurtainState()
        state.observe(YouTubeCurtainScreen.SEARCH, 0)
        state.request(YouTubeCurtainScreen.HOME, 10)
        assertEquals(YouTubeProtectedSurface.HOME, state.observe(YouTubeCurtainScreen.HOME, 20))
        val token = state.request(YouTubeCurtainScreen.YOU, 30)
        // A single transitional search frame keeps the request pending and the
        // existing feed coverage.
        assertEquals(YouTubeProtectedSurface.HOME,
            state.observe(YouTubeCurtainScreen.SEARCH, 40, YouTubeNavigationDestination.YOU))
        assertTrue(state.isPending)
        // ...the confirmed consecutive frame releases it without the timeout.
        assertNull(state.observe(YouTubeCurtainScreen.SEARCH, 240, YouTubeNavigationDestination.YOU))
        assertFalse(state.isPending)
        assertTrue(state.isCurrentGeneration(token))
        assertNull(state.observe(YouTubeCurtainScreen.YOU, 250, YouTubeNavigationDestination.YOU))
    }

    @Test
    fun `outgoing search on Home selection cannot prematurely complete You request`() {
        val state = YouTubeCurtainState()
        state.observe(YouTubeCurtainScreen.HOME, 0)
        state.request(YouTubeCurtainScreen.YOU, 10)
        assertEquals(YouTubeProtectedSurface.HOME,
            state.observe(YouTubeCurtainScreen.SEARCH, 20, YouTubeNavigationDestination.HOME))
        assertTrue(state.isPending)
        assertEquals(YouTubeProtectedSurface.HOME,
            state.observe(YouTubeCurtainScreen.UNKNOWN, 30, YouTubeNavigationDestination.YOU))
        assertTrue(state.isPending)
    }

    @Test
    fun `restored search on the selected Home tab completes after a confirmed streak`() {
        val state = YouTubeCurtainState()
        state.observe(YouTubeCurtainScreen.SUBSCRIPTIONS, 0)
        state.request(YouTubeCurtainScreen.HOME, 10)
        assertEquals(YouTubeProtectedSurface.HOME, state.currentSurface)
        // One transitional search frame must not release the curtain...
        assertEquals(YouTubeProtectedSurface.HOME,
            state.observe(YouTubeCurtainScreen.SEARCH, 20, YouTubeNavigationDestination.HOME))
        assertTrue(state.isPending)
        // ...the confirmed consecutive frame completes the pending request.
        assertNull(state.observe(YouTubeCurtainScreen.SEARCH, 220, YouTubeNavigationDestination.HOME))
        assertFalse(state.isPending)
        assertNull(state.observe(YouTubeCurtainScreen.UNKNOWN, 230, YouTubeNavigationDestination.HOME))
    }

    @Test
    fun `feed flash between search frames keeps the curtain without a blink`() {
        val state = YouTubeCurtainState()
        state.observe(YouTubeCurtainScreen.SUBSCRIPTIONS, 0)
        state.request(YouTubeCurtainScreen.HOME, 10)
        // Home-tab restore: one search frame, a brief feed flash, then the
        // search settles. The curtain stays mounted through all of it and
        // lifts once - no down-up-down blink.
        assertEquals(YouTubeProtectedSurface.HOME,
            state.observe(YouTubeCurtainScreen.SEARCH, 20, YouTubeNavigationDestination.HOME))
        assertTrue(state.isPending)
        assertEquals(YouTubeProtectedSurface.HOME,
            state.observe(YouTubeCurtainScreen.HOME, 60, YouTubeNavigationDestination.HOME))
        assertFalse(state.isPending)
        assertNull(state.observe(YouTubeCurtainScreen.SEARCH, 80, YouTubeNavigationDestination.HOME))
        assertNull(state.observe(YouTubeCurtainScreen.SEARCH, 280, YouTubeNavigationDestination.HOME))
    }

    @Test
    fun `alternating transitional frames still complete within the bounded deadline`() {
        val state = YouTubeCurtainState()
        state.observe(YouTubeCurtainScreen.SUBSCRIPTIONS, 0)
        state.request(YouTubeCurtainScreen.HOME, 10)
        assertEquals(YouTubeProtectedSurface.HOME,
            state.observe(YouTubeCurtainScreen.SEARCH, 20, YouTubeNavigationDestination.HOME))
        assertEquals(YouTubeProtectedSurface.HOME,
            state.observe(YouTubeCurtainScreen.UNKNOWN, 400, YouTubeNavigationDestination.HOME))
        assertNull(state.observe(YouTubeCurtainScreen.SEARCH, 930, YouTubeNavigationDestination.HOME))
        assertFalse(state.isPending)
    }

    @Test
    fun `outgoing search while switching to Subscriptions keeps coverage until the feed settles`() {
        val state = YouTubeCurtainState()
        state.observe(YouTubeCurtainScreen.SEARCH, 0)
        state.request(YouTubeCurtainScreen.SUBSCRIPTIONS, 10)
        // The outgoing search stays in the tree while the Subscriptions tab is
        // already selected; it must not release the pending request.
        assertEquals(YouTubeProtectedSurface.SUBSCRIPTIONS,
            state.observe(YouTubeCurtainScreen.SEARCH, 20, YouTubeNavigationDestination.SUBSCRIPTIONS))
        assertEquals(YouTubeProtectedSurface.SUBSCRIPTIONS,
            state.observe(YouTubeCurtainScreen.SEARCH, 240, YouTubeNavigationDestination.SUBSCRIPTIONS))
        assertTrue(state.isPending)
        // The feed settles: completion happens on the target itself.
        assertEquals(YouTubeProtectedSurface.SUBSCRIPTIONS,
            state.observe(YouTubeCurtainScreen.SUBSCRIPTIONS, 300, YouTubeNavigationDestination.SUBSCRIPTIONS))
        assertFalse(state.isPending)
    }

    @Test
    fun `rapid Subscriptions then Home bounce completes on the restored search`() {
        val state = YouTubeCurtainState()
        state.observe(YouTubeCurtainScreen.SEARCH, 0)
        state.request(YouTubeCurtainScreen.SUBSCRIPTIONS, 10)
        // The user bounces back to Home before the feed settles. The last
        // verified screen is still the search, but the replacement request
        // must not suppress the Home tab's restored search page.
        state.request(YouTubeCurtainScreen.HOME, 30)
        assertEquals(YouTubeProtectedSurface.HOME,
            state.observe(YouTubeCurtainScreen.SEARCH, 60, YouTubeNavigationDestination.HOME))
        assertTrue(state.isPending)
        assertNull(state.observe(YouTubeCurtainScreen.SEARCH, 260, YouTubeNavigationDestination.HOME))
        assertFalse(state.isPending)
    }

    @Test
    fun `duplicate Home click echo after a restored search does not re-cover`() {
        val state = YouTubeCurtainState()
        state.observe(YouTubeCurtainScreen.SEARCH, 0)
        state.request(YouTubeCurtainScreen.SUBSCRIPTIONS, 10)
        state.request(YouTubeCurtainScreen.HOME, 30)
        assertEquals(YouTubeProtectedSurface.HOME,
            state.observe(YouTubeCurtainScreen.SEARCH, 60, YouTubeNavigationDestination.HOME))
        assertNull(state.observe(YouTubeCurtainScreen.SEARCH, 260, YouTubeNavigationDestination.HOME))
        assertFalse(state.isPending)
        // YouTube's tab bar delivers a duplicate click event for the same tab
        // right after the restored page released the curtain.
        state.request(YouTubeCurtainScreen.HOME, 350)
        assertFalse(state.isPending)
        assertNull(state.observe(YouTubeCurtainScreen.SEARCH, 380, YouTubeNavigationDestination.HOME))
    }

    @Test
    fun `duplicate Home click echo after the feed flash does not re-cover the search`() {
        val state = YouTubeCurtainState()
        state.observe(YouTubeCurtainScreen.SUBSCRIPTIONS, 0)
        state.request(YouTubeCurtainScreen.HOME, 10)
        assertEquals(YouTubeProtectedSurface.HOME,
            state.observe(YouTubeCurtainScreen.HOME, 20, YouTubeNavigationDestination.HOME))
        assertFalse(state.isPending)
        assertNull(state.observe(YouTubeCurtainScreen.SEARCH, 300, YouTubeNavigationDestination.HOME))
        // The duplicate click arrives while the restored search is visible.
        state.request(YouTubeCurtainScreen.HOME, 350)
        assertFalse(state.isPending)
        assertNull(state.observe(YouTubeCurtainScreen.SEARCH, 380, YouTubeNavigationDestination.HOME))
    }

    @Test
    fun `loading selected feed coverage does not pin a verified secondary page`() {
        for (allowed in listOf(YouTubeCurtainScreen.SEARCH, YouTubeCurtainScreen.NOTIFICATIONS)) {
            val state = YouTubeCurtainState()
            state.observe(YouTubeCurtainScreen.HOME, 0)
            assertEquals(YouTubeProtectedSurface.HOME, state.observe(YouTubeCurtainScreen.UNKNOWN, 10))
            assertFalse(state.isPending)
            assertNull(state.observe(allowed, 20))
        }
    }

    @Test
    fun `Home request covers before You animation and settles at first matching frame`() {
        val state = YouTubeCurtainState()
        state.observe(YouTubeCurtainScreen.YOU, 0)
        state.request(YouTubeCurtainScreen.HOME, 10)
        assertEquals(YouTubeProtectedSurface.HOME, state.currentSurface)
        assertEquals(YouTubeProtectedSurface.HOME, state.observe(YouTubeCurtainScreen.YOU, 20))
        state.observe(YouTubeCurtainScreen.HOME, 30)
        assertFalse(state.isPending)
        assertEquals(YouTubeProtectedSurface.HOME, state.observe(YouTubeCurtainScreen.UNKNOWN, 40))
        assertNull(state.observe(YouTubeCurtainScreen.YOU, 50))
    }

    @Test
    fun `You request retains feed coverage until verified and system Back can protect Home`() {
        val state = YouTubeCurtainState()
        state.observe(YouTubeCurtainScreen.HOME, 0)
        state.request(YouTubeCurtainScreen.YOU, 10)
        assertEquals(YouTubeProtectedSurface.HOME, state.observe(YouTubeCurtainScreen.HOME, 20))
        assertNull(state.observe(YouTubeCurtainScreen.YOU, 30))
        assertFalse(state.isPending)
        assertNull(state.observe(YouTubeCurtainScreen.UNKNOWN, 40))
        assertEquals(YouTubeProtectedSurface.HOME, state.observe(YouTubeCurtainScreen.HOME, 50))
    }

    @Test
    fun `rapid Home You Home sequence keeps newest intent and invalidates old dispatches`() {
        val state = YouTubeCurtainState()
        state.observe(YouTubeCurtainScreen.YOU, 0)
        val firstHome = state.request(YouTubeCurtainScreen.HOME, 10)
        val you = state.request(YouTubeCurtainScreen.YOU, 20)
        val finalHome = state.request(YouTubeCurtainScreen.HOME, 30)
        assertFalse(state.isCurrentGeneration(firstHome))
        assertFalse(state.isCurrentGeneration(you))
        assertTrue(state.isCurrentGeneration(finalHome))
        assertEquals(YouTubeProtectedSurface.HOME, state.observe(YouTubeCurtainScreen.YOU, 40))
        assertEquals(YouTubeCurtainScreen.HOME, state.pendingDestination)
        state.observe(YouTubeCurtainScreen.HOME, 50)
        assertFalse(state.isPending)
    }

    @Test
    fun `rapid subscriptions Home You holds protection through outgoing protected frames`() {
        val state = YouTubeCurtainState()
        state.observe(YouTubeCurtainScreen.SUBSCRIPTIONS, 0)
        state.request(YouTubeCurtainScreen.HOME, 10)
        state.request(YouTubeCurtainScreen.YOU, 20)
        assertEquals(YouTubeProtectedSurface.HOME, state.observe(YouTubeCurtainScreen.SUBSCRIPTIONS, 30))
        assertEquals(YouTubeProtectedSurface.HOME, state.observe(YouTubeCurtainScreen.HOME, 40))
        assertNull(state.observe(YouTubeCurtainScreen.YOU, 50))
        assertNull(state.observe(YouTubeCurtainScreen.UNKNOWN, 60))
    }

    @Test
    fun `Notifications Back then reentry preserves coverage until confirmed and invalidates Back`() {
        val state = YouTubeCurtainState()
        state.observe(YouTubeCurtainScreen.NOTIFICATIONS, 0)
        val back = state.request(YouTubeCurtainScreen.HOME, 10)
        assertEquals(YouTubeProtectedSurface.HOME, state.observe(YouTubeCurtainScreen.NOTIFICATIONS, 20))
        state.observe(YouTubeCurtainScreen.HOME, 30)
        state.request(YouTubeCurtainScreen.NOTIFICATIONS, 40)
        assertFalse(state.isCurrentGeneration(back))
        assertEquals(YouTubeProtectedSurface.HOME, state.observe(YouTubeCurtainScreen.HOME, 50))
        assertNull(state.observe(YouTubeCurtainScreen.NOTIFICATIONS, 60))
    }

    @Test
    fun `unrequested verified screen updates while unknown preserves current coverage`() {
        val state = YouTubeCurtainState()
        assertEquals(YouTubeProtectedSurface.SUBSCRIPTIONS, state.observe(YouTubeCurtainScreen.SUBSCRIPTIONS, 0))
        assertEquals(YouTubeProtectedSurface.SUBSCRIPTIONS, state.observe(YouTubeCurtainScreen.UNKNOWN, 10))
        assertNull(state.observe(YouTubeCurtainScreen.SEARCH, 20))
        assertNull(state.observe(YouTubeCurtainScreen.UNKNOWN, 30))
    }

    @Test
    fun `failed request times out and invalidates delayed work using current verified page`() {
        val state = YouTubeCurtainState(requestTimeoutMs = 100)
        state.observe(YouTubeCurtainScreen.YOU, 0)
        val token = state.request(YouTubeCurtainScreen.HOME, 10)
        assertEquals(YouTubeProtectedSurface.HOME, state.observe(YouTubeCurtainScreen.YOU, 109))
        assertNull(state.observe(YouTubeCurtainScreen.YOU, 110))
        assertFalse(state.isPending)
        assertFalse(state.isCurrentGeneration(token))
        assertEquals(YouTubeProtectedSurface.SUBSCRIPTIONS, state.observe(YouTubeCurtainScreen.SUBSCRIPTIONS, 120))
    }

    @Test
    fun `timeout with unknown retains protection until reliable page exists`() {
        val state = YouTubeCurtainState(requestTimeoutMs = 100)
        state.request(YouTubeCurtainScreen.HOME, 0)
        assertEquals(YouTubeProtectedSurface.HOME, state.observe(YouTubeCurtainScreen.UNKNOWN, 100))
        assertFalse(state.isPending)
        assertNull(state.observe(YouTubeCurtainScreen.YOU, 110))
    }

    @Test
    fun `foreground departure clears screen and rejects all previous asynchronous work`() {
        val state = YouTubeCurtainState()
        val token = state.request(YouTubeCurtainScreen.HOME, 0)
        state.clear()
        assertFalse(state.isCurrentGeneration(token))
        assertFalse(state.isPending)
        assertNull(state.currentSurface)
        assertNull(state.observe(YouTubeCurtainScreen.UNKNOWN, 10))
        assertEquals(YouTubeProtectedSurface.HOME, state.observe(YouTubeCurtainScreen.HOME, 20))
    }
}
