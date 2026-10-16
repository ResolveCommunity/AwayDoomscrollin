package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramReelsSessionTest {
    private val balanced = ProtectionMode.BALANCED
    @Test fun strictBlocksChosenReel() {
        assertEquals(InstagramReelsDecision.EXIT,
            InstagramReelsSession().observe(ProtectionMode.STRICT, true))
    }
    @Test fun bothModesBlockFeedTab() {
        ProtectionMode.entries.forEach { mode ->
            assertEquals(InstagramReelsDecision.EXIT, InstagramReelsSession().observe(mode, false, feedEntry = true))
        }
    }
    @Test fun balancedAllowsFirstVideoAndOpeningZeroMovement() {
        val session = InstagramReelsSession()
        assertEquals(InstagramReelsDecision.ALLOW, session.observe(balanced, true))
        assertEquals(InstagramReelsDecision.ALLOW,
            session.observe(balanced, true, pagerScroll = true, verticalMovement = false))
    }
    @Test fun nextVideoExitsButCommentScrollDoesNot() {
        val session = InstagramReelsSession()
        session.observe(balanced, true)
        assertEquals(InstagramReelsDecision.ALLOW, session.observe(balanced, true,
            safeSheetOpen = true, pagerScroll = true, verticalMovement = true))
        assertEquals(InstagramReelsDecision.ALLOW, session.observe(balanced, true,
            pagerScroll = false, verticalMovement = true))
        assertEquals(InstagramReelsDecision.EXIT, session.observe(balanced, true,
            pagerScroll = true, verticalMovement = true))
    }
    @Test fun missingTreeCannotReopenExitedViewer() {
        val session = InstagramReelsSession()
        session.observe(balanced, true, feedEntry = true)
        val token = session.generation
        repeat(30) {
            assertEquals(InstagramReelsDecision.EXIT, session.observe(balanced, false))
        }
        assertEquals(token, session.generation)
    }
    @Test fun verifiedReturnAllowsNewIntentionalVideo() {
        val session = InstagramReelsSession()
        repeat(50) {
            assertEquals(InstagramReelsDecision.ALLOW, session.observe(balanced, true))
            assertEquals(InstagramReelsDecision.EXIT, session.observe(balanced, true,
                pagerScroll = true, verticalMovement = true))
            session.observe(balanced, false, verifiedOutside = true)
        }
    }
    @Test fun backgroundHostIsNotARealReturn() {
        val session = InstagramReelsSession()
        session.observe(balanced, true, feedEntry = true)
        assertEquals(InstagramReelsDecision.EXIT,
            session.observe(balanced, true, verifiedOutside = true))
    }
    @Test fun modeChangeToStrictClosesBalancedViewer() {
        val session = InstagramReelsSession()
        session.observe(balanced, true)
        assertEquals(InstagramReelsDecision.EXIT, session.observe(ProtectionMode.STRICT, true))
    }
    @Test fun longVideosAndUnrelatedScrollAreNotReels() {
        val session = InstagramReelsSession()
        assertEquals(InstagramReelsDecision.ALLOW, session.observe(ProtectionMode.STRICT, false,
            pagerScroll = false, verticalMovement = true))
        assertFalse(session.watching)
    }
}
