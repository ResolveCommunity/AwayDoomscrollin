package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Test

class InstagramScrollGeometryTest {
    @Test fun strongUpwardMotionClosesRemainingStoryStripTemporarily() {
        assertEquals(100, InstagramScrollGeometry.top(300, 285, 100, 0, 60, 48))
    }
    @Test fun horizontalFlingStillLeavesStoriesAvailable() {
        assertEquals(300, InstagramScrollGeometry.top(300, 300, 100, 150, 60, 48))
    }
    @Test fun slowMotionContinuesTrackingTray() {
        assertEquals(290, InstagramScrollGeometry.top(300, 300, 100, 0, 10, 48))
    }
    @Test fun upwardScrollCoversGapBeforeNewBoundsArrive() {
        assertEquals(260, InstagramScrollGeometry.top(300, 300, 100, 0, 40))
    }
    @Test fun freshBoundsCanExpandProtectionFurther() {
        assertEquals(220, InstagramScrollGeometry.top(300, 220, 100, 0, 40))
    }
    @Test fun largeScrollNeverCoversHeader() {
        assertEquals(100, InstagramScrollGeometry.top(300, 200, 100, 0, Int.MAX_VALUE))
    }
    @Test fun downwardScrollUsesMeasuredStoriesBoundary() {
        assertEquals(330, InstagramScrollGeometry.top(300, 330, 100, 0, -80))
    }
    @Test fun horizontalStoriesScrollDoesNotPredictFeedMovement() {
        assertEquals(300, InstagramScrollGeometry.top(300, 300, 100, 200, 10))
    }
    @Test fun missingDeltaUsesCurrentBounds() {
        assertEquals(240, InstagramScrollGeometry.top(300, 240, 100, 0, 0))
    }
}
