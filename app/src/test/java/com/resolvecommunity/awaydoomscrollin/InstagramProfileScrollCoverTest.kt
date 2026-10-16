package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramProfileScrollCoverTest {
    @Test fun smallSwipeNeverCoversTheWholeProfileHeader() {
        val cover = InstagramProfileScrollCover()
        cover.measure(1664, 266, 0)
        assertEquals(1652, cover.onScroll(1664, 266, 10, 12))
        assertEquals(1652, cover.measure(1664, 266, 20))
        assertEquals(1652, cover.measure(1652, 266, 30))
        assertFalse(cover.isHolding)
    }
    @Test fun captured495PixelSwipeFollowsRealGridNotToolbar() {
        val cover = InstagramProfileScrollCover()
        cover.measure(1664, 266, 0)
        assertEquals(1169, cover.onScroll(1664, 266, 10, 495))
        assertEquals(1169, cover.measure(1169, 266, 20))
        assertEquals(1568, cover.measure(1568, 266, 30))
    }
    @Test fun zeroOrLateScrollDoesNotInventDisplacement() {
        val cover = InstagramProfileScrollCover()
        assertEquals(1664, cover.onScroll(1664, 266, 0))
        assertEquals(1664, cover.measure(1664, 266, 100))
    }
    @Test fun measuredUpwardMotionTracksTheBoundaryDirectly() {
        val cover = InstagramProfileScrollCover()
        assertEquals(1000, cover.measure(1000, 200, 0))
        assertEquals(920, cover.measure(920, 200, 100))
        assertEquals(800, cover.measure(800, 200, 110))
        assertFalse(cover.isHolding)
    }
    @Test fun reversedGestureDoesNotHoldAnOutdatedHighCurtain() {
        val cover = InstagramProfileScrollCover()
        cover.measure(1000, 200, 0)
        cover.onScroll(1000, 200, 10, 80)
        assertEquals(1080, cover.measure(1080, 200, 20))
        assertFalse(cover.isHolding)
    }
    @Test fun unpublishedPredictionExpiresOnlyAfterQuietStableMeasurements() {
        val cover = InstagramProfileScrollCover()
        cover.measure(1000, 200, 0)
        cover.onScroll(1000, 200, 10, 80)
        assertEquals(920, cover.measure(1000, 200, 100))
        assertEquals(920, cover.measure(1000, 200, 200))
        assertEquals(1000, cover.measure(1000, 200, 300))
    }
    @Test fun predictionNeverCrossesToolbarAndResetClearsIt() {
        val cover = InstagramProfileScrollCover()
        assertEquals(200, cover.onScroll(1000, 200, 0, Int.MAX_VALUE))
        cover.reset()
        assertEquals(1100, cover.measure(1100, 200, 100))
    }
    @Test fun strictConservativeScrollAddsSafetyAndWaitsForStableGeometry() {
        val cover = InstagramProfileScrollCover()
        cover.measure(1609, 266, 0)
        assertEquals(1440, cover.onScroll(
            previousTop = 1609,
            headerFloor = 266,
            now = 10,
            movementPixels = 113,
            safetyMarginPixels = 56,
            settleConservatively = true))
        // A temporary 1496 boundary cannot reopen the band above the curtain.
        assertEquals(1440, cover.measure(1496, 266, 20))
        // Real geometry may move above the prediction and is followed safely.
        assertEquals(1400, cover.measure(1400, 266, 40))
        assertEquals(1400, cover.measure(1468, 266, 300))
        assertEquals(1400, cover.measure(1468, 266, 310))
        assertEquals(1400, cover.measure(1468, 266, 320))
        assertFalse(cover.isHolding)
    }
    @Test fun strictSessionNeverShrinksAfterAProfileScroll() {
        val cover = InstagramProfileScrollCover()
        cover.measure(896, 266, 0)
        assertEquals(769, cover.onScroll(
            previousTop = 896,
            headerFloor = 266,
            now = 10,
            movementPixels = 71,
            safetyMarginPixels = 56,
            settleConservatively = true))
        repeat(3) { index -> cover.measure(1609, 266, 300L + index * 10L) }
        assertFalse(cover.isHolding)
        assertEquals(769, cover.measure(1276, 266, 400))
        assertEquals(740, cover.measure(740, 266, 410))
        assertEquals(740, cover.measure(1103, 266, 420))
        cover.reset()
        assertEquals(1103, cover.measure(1103, 266, 430))
    }
    @Test fun horizontalAndUnknownZeroDeltaEventsAreNotVerticalScrolling() {
        assertTrue(InstagramProfileScrollCover.shouldCover(0, 12, 12, false))
        assertFalse(InstagramProfileScrollCover.shouldCover(12, 0, 0, false))
        assertFalse(InstagramProfileScrollCover.shouldCover(0, 0, 12, false))
        assertTrue(InstagramProfileScrollCover.shouldCover(0, 0, 12, true))
    }
}
