package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Contract under test: entering a Short and watching it to the end is never interrupted;
 * only the first vertical swipe toward the next video exits the viewer. Comment and
 * bottom-sheet scrolling stay usable. No time limit, quota or pattern rule exists.
 */
class YouTubeShortsPolicyTest {
    @Test
    fun `shared curtain allows balanced intentional Shorts but strict rejects them`() {
        for (mode in ProtectionMode.entries) {
            val curtain = YouTubeCurtainState()
            assertEquals(YouTubeProtectedSurface.HOME, curtain.observe(YouTubeCurtainScreen.HOME, 0))
            assertEquals(YouTubeProtectedSurface.SUBSCRIPTIONS, curtain.observe(YouTubeCurtainScreen.SUBSCRIPTIONS, 10))
            assertNull(curtain.observe(YouTubeCurtainScreen.SEARCH, 20))
            assertNull(curtain.observe(YouTubeCurtainScreen.SHORTS, 30))
            assertEquals(mode == ProtectionMode.STRICT, YouTubeShortsPolicy.shouldBlockEntry(mode, false))
            assertTrue(YouTubeShortsPolicy.shouldBlockEntry(mode, true))
            assertEquals(YouTubeProtectedSurface.HOME, curtain.observe(YouTubeCurtainScreen.HOME, 40))
            assertNull(curtain.observe(YouTubeCurtainScreen.YOU, 50))
        }
    }
    @Test
    fun `strict rejects both feed tab and intentional single video entry`() {
        assertTrue(YouTubeShortsPolicy.shouldBlockEntry(ProtectionMode.STRICT, true))
        assertTrue(YouTubeShortsPolicy.shouldBlockEntry(ProtectionMode.STRICT, false))
    }

    @Test
    fun `balanced rejects feed tab but allows search and link entry`() {
        assertTrue(YouTubeShortsPolicy.shouldBlockEntry(ProtectionMode.BALANCED, true))
        assertFalse(YouTubeShortsPolicy.shouldBlockEntry(ProtectionMode.BALANCED, false))
    }
    @Test
    fun `opening a Short without scrolling never exits`() {
        assertFalse(
            YouTubeShortsPolicy.shouldExit(
                protectionEnabled = true,
                isScrollEvent = false,
                packageInForeground = true,
                shortsVisible = true,
                isVerticalScroll = false,
                isSafeSourcePath = false
            )
        )
    }

    @Test
    fun `first vertical swipe on a visible Shorts viewer exits`() {
        assertTrue(
            YouTubeShortsPolicy.shouldExit(
                protectionEnabled = true,
                isScrollEvent = true,
                packageInForeground = true,
                shortsVisible = true,
                isVerticalScroll = true,
                isSafeSourcePath = false
            )
        )
    }

    @Test
    fun `comment and bottom sheet scrolling never exits`() {
        assertFalse(
            YouTubeShortsPolicy.shouldExit(
                protectionEnabled = true,
                isScrollEvent = true,
                packageInForeground = true,
                shortsVisible = true,
                isVerticalScroll = true,
                isSafeSourcePath = true
            )
        )
    }

    @Test
    fun `disabled background off-screen and horizontal cases never exit`() {
        assertFalse(YouTubeShortsPolicy.shouldExit(false, true, true, true, true, false))
        assertFalse(YouTubeShortsPolicy.shouldExit(true, true, false, true, true, false))
        assertFalse(YouTubeShortsPolicy.shouldExit(true, true, true, false, true, false))
        assertFalse(YouTubeShortsPolicy.shouldExit(true, true, true, true, false, false))
    }

    @Test
    fun `content or window change events can never trigger the exit`() {
        // The service routes only TYPE_VIEW_SCROLLED through shouldExit; these assertions
        // pin the guard so a viewer that just opened is never dismissed without a swipe.
        assertFalse(YouTubeShortsPolicy.shouldExit(true, isScrollEvent = false, true, true, true, false))
    }

}
