package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract under test: entering a Short and watching it to the end is never interrupted;
 * only the first vertical swipe toward the next video exits the viewer. Comment and
 * bottom-sheet scrolling stay usable. No time limit, quota or pattern rule exists.
 */
class YouTubeShortsPolicyTest {
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

    @Test
    fun `cooldown is scoped to accepted Shorts exits`() {
        assertTrue(YouTubeShortsPolicy.cooldownElapsed(nowMs = 1_000L, lastAcceptedExitMs = 0L))
        assertFalse(YouTubeShortsPolicy.cooldownElapsed(nowMs = 1_599L, lastAcceptedExitMs = 1_000L))
        assertTrue(YouTubeShortsPolicy.cooldownElapsed(nowMs = 1_600L, lastAcceptedExitMs = 1_000L))
    }
}
