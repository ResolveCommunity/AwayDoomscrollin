package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramReelShadeHoldTest {
    @Test
    fun `same viewer returning from the shade keeps its origin`() {
        val hold = InstagramReelShadeHold()
        hold.hold(viewerWindowId = 35, origin = InstagramScreen.PROFILE, nowMs = 1_000L)
        assertTrue(hold.isHeld)
        assertEquals(InstagramScreen.PROFILE, hold.take(35, 4_000L))
        assertFalse(hold.isHeld)
    }

    @Test
    fun `a different viewer window is not restored and consumes the hold`() {
        val hold = InstagramReelShadeHold()
        hold.hold(35, InstagramScreen.PROFILE, 1_000L)
        assertNull(hold.take(64, 2_000L))
        assertNull(hold.take(35, 2_100L))
    }

    @Test
    fun `an expired hold is not restored`() {
        val hold = InstagramReelShadeHold()
        hold.hold(35, InstagramScreen.DIRECT_MESSAGES, 1_000L)
        assertNull(hold.take(35, 1_000L + InstagramReelShadeHold.TTL_MS))
    }

    @Test
    fun `unknown viewer windows are never held`() {
        val hold = InstagramReelShadeHold()
        hold.hold(-1, InstagramScreen.PROFILE, 1_000L)
        assertFalse(hold.isHeld)
    }
}
