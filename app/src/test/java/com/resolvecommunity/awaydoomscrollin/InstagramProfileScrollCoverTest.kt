package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramProfileScrollCoverTest {
    @Test fun coversImmediatelyWithoutWaitingForMeasurement() {
        val cover = InstagramProfileScrollCover()
        assertEquals(200, cover.onScroll(1000, 200, 0))
        assertEquals(200, cover.measure(900, 200, 100))
    }
    @Test fun quietAndStableMeasurementsAreBothRequired() {
        val cover = InstagramProfileScrollCover()
        cover.onScroll(1000, 200, 0)
        assertEquals(200, cover.measure(900, 200, 300))
        assertEquals(200, cover.measure(800, 200, 400))
        assertEquals(200, cover.measure(800, 200, 500))
        assertEquals(800, cover.measure(800, 200, 600))
        assertFalse(cover.isHolding)
    }
    @Test fun anotherScrollRestartsSettlement() {
        val cover = InstagramProfileScrollCover()
        cover.onScroll(1000, 200, 0)
        cover.measure(800, 200, 300)
        cover.measure(800, 200, 400)
        cover.onScroll(200, 200, 450)
        assertEquals(200, cover.measure(800, 200, 500))
        assertTrue(cover.isHolding)
    }
    @Test fun leavingProfileClearsHeldGeometry() {
        val cover = InstagramProfileScrollCover()
        cover.onScroll(1000, 200, 0)
        cover.reset()
        assertEquals(1100, cover.measure(1100, 200, 100))
    }

    @Test fun measuredUpwardGridMotionCoversWhenInstagramReportsZeroDelta() {
        val cover = InstagramProfileScrollCover()
        assertEquals(1000, cover.measure(1000, 200, 0))
        assertEquals(200, cover.measure(920, 200, 100))
        assertTrue(cover.isHolding)
    }

    @Test fun downwardOrStableGeometryDoesNotCoverProfileInformation() {
        val cover = InstagramProfileScrollCover()
        assertEquals(900, cover.measure(900, 200, 0))
        assertEquals(900, cover.measure(900, 200, 100))
        assertEquals(980, cover.measure(980, 200, 200))
        assertFalse(cover.isHolding)
    }

    @Test fun explicitVerticalMotionCoversEvenWhenInstagramChangesTheSourceId() {
        assertTrue(InstagramProfileScrollCover.shouldCover(0, 12, 12, false))
        assertFalse(InstagramProfileScrollCover.shouldCover(12, 0, 0, false))
        assertFalse(InstagramProfileScrollCover.shouldCover(0, 0, 12, false))
        assertTrue(InstagramProfileScrollCover.shouldCover(0, 0, 12, true))
    }
}
