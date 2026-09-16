package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokInterventionPolicyTest {
    @Test
    fun `opening TikTok without a scroll never exits`() {
        assertFalse(
            TikTokInterventionPolicy.shouldExit(
                protectionEnabled = true,
                isScrollEvent = false,
                packageInForeground = true,
                isVerticalScroll = false,
                isSafeSurface = false
            )
        )
    }

    @Test
    fun `first vertical scroll on a feed surface exits`() {
        assertTrue(
            TikTokInterventionPolicy.shouldExit(
                protectionEnabled = true,
                isScrollEvent = true,
                packageInForeground = true,
                isVerticalScroll = true,
                isSafeSurface = false
            )
        )
    }

    @Test
    fun `safe and unknown surfaces never exit`() {
        assertFalse(
            TikTokInterventionPolicy.shouldExit(
                protectionEnabled = true,
                isScrollEvent = true,
                packageInForeground = true,
                isVerticalScroll = true,
                isSafeSurface = true
            )
        )
    }

    @Test
    fun `disabled background and horizontal cases never exit`() {
        assertFalse(TikTokInterventionPolicy.shouldExit(false, true, true, true, false))
        assertFalse(TikTokInterventionPolicy.shouldExit(true, true, false, true, false))
        assertFalse(TikTokInterventionPolicy.shouldExit(true, true, true, false, false))
    }

    @Test
    fun `cooldown is scoped to accepted TikTok exits`() {
        assertTrue(TikTokInterventionPolicy.cooldownElapsed(nowMs = 1_000L, lastAcceptedExitMs = 0L))
        assertFalse(TikTokInterventionPolicy.cooldownElapsed(nowMs = 1_599L, lastAcceptedExitMs = 1_000L))
        assertTrue(TikTokInterventionPolicy.cooldownElapsed(nowMs = 1_600L, lastAcceptedExitMs = 1_000L))
    }

    @Test
    fun `top tab safety net checks are throttled`() {
        assertTrue(TikTokInterventionPolicy.topTabCheckElapsed(nowMs = 1_000L, lastCheckMs = 0L))
        assertFalse(
            TikTokInterventionPolicy.topTabCheckElapsed(
                nowMs = 1_000L + TikTokInterventionPolicy.TOP_TAB_CHECK_MIN_INTERVAL_MS - 1,
                lastCheckMs = 1_000L
            )
        )
        assertTrue(
            TikTokInterventionPolicy.topTabCheckElapsed(
                nowMs = 1_000L + TikTokInterventionPolicy.TOP_TAB_CHECK_MIN_INTERVAL_MS,
                lastCheckMs = 1_000L
            )
        )
    }
}
