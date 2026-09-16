package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.*
import org.junit.Test

class InstagramProfileGeometryTest {
    @Test fun excludesProfileHeaderStoriesAndNavigation() {
        assertEquals(900 to 2100, InstagramProfileGeometry.clip(200, 2300, 900, 2100))
    }
    @Test fun followsCollapsedProfileHeader() {
        assertEquals(300 to 2100, InstagramProfileGeometry.clip(200, 2300, 300, 2100))
    }
    @Test fun neverExpandsBeyondPager() {
        assertEquals(900 to 2000, InstagramProfileGeometry.clip(900, 2000, 300, 2100))
    }
    @Test fun missingChromeUsesGridNotEntireScreen() {
        assertEquals(900 to 2000, InstagramProfileGeometry.clip(900, 2000, null, null))
    }
    @Test fun offscreenGridDoesNotCoverProfileInformation() {
        assertNull(InstagramProfileGeometry.clip(200, 800, 900, 2100))
    }
    @Test fun scrollCoverStopsBelowObservedProfileActionBar() {
        assertEquals(266, InstagramProfileGeometry.scrollFloor(0, 266, 98, 168))
    }
    @Test fun missingActionBarUsesStatusAndToolbarInsteadOfDisplayTop() {
        assertEquals(266, InstagramProfileGeometry.scrollFloor(0, null, 98, 168))
    }
    @Test fun partialStatusBarNodeCannotPullCurtainOverToolbar() {
        assertEquals(266, InstagramProfileGeometry.scrollFloor(0, 126, 98, 168))
    }
}
