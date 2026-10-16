package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramNavigationGeometryTest {
    @Test fun instantMovesKeepSearchChromeOpenWithoutReopeningTheGrid() {
        val target = InstagramNavigationGeometry.transitionTop(98, 266, true)
        assertEquals(266, target)
        assertEquals(266, InstagramNavigationGeometry.top(655, target,
            InstagramScreen.HOME_FEED, true, true))
        assertEquals(266, InstagramNavigationGeometry.top(266, target,
            InstagramScreen.EXPLORE, true, true))
    }

    @Test fun animatedOlderWindowsRetainConservativeCover() {
        assertEquals(98, InstagramNavigationGeometry.transitionTop(98, 266, false))
        assertEquals(98, InstagramNavigationGeometry.transitionTop(98, 80, true))
    }

    @Test fun chromeRestoreOnlyUpdatesTheCurrentExploreCurtain() {
        assertTrue(InstagramNavigationGeometry.canRestoreSearchChrome(
            InstagramScreen.EXPLORE, true, true))
        assertFalse(InstagramNavigationGeometry.canRestoreSearchChrome(
            InstagramScreen.EXPLORE, false, true))
        assertFalse(InstagramNavigationGeometry.canRestoreSearchChrome(
            InstagramScreen.EXPLORE, true, false))
        for (screen in InstagramScreen.entries.filter { it != InstagramScreen.EXPLORE }) {
            assertFalse(screen.name,
                InstagramNavigationGeometry.canRestoreSearchChrome(screen, true, true))
        }
    }

    @Test fun exploreAnimationCoversMovingHeaderRegion() {
        assertEquals(98, InstagramNavigationGeometry.top(266, 98,
            InstagramScreen.EXPLORE, true, true))
        assertEquals(80, InstagramNavigationGeometry.top(80, 98,
            InstagramScreen.EXPLORE, true, true))
    }

    @Test fun settledOrDifferentWindowRestoresMeasuredTop() {
        assertEquals(266, InstagramNavigationGeometry.top(266, 98,
            InstagramScreen.EXPLORE, true, false))
        assertEquals(266, InstagramNavigationGeometry.top(266, 98,
            InstagramScreen.EXPLORE, false, true))
    }

    @Test fun outgoingHomeMeasurementCannotReopenExploreTopRow() {
        assertEquals(98, InstagramNavigationGeometry.top(655, 98,
            InstagramScreen.HOME_FEED, true, true))
        assertEquals(655, InstagramNavigationGeometry.top(655, 98,
            InstagramScreen.HOME_FEED, true, false))
    }

    @Test fun holdCannotCoverMessagingSearchResultsOrOtherScreens() {
        for (screen in InstagramScreen.entries.filter {
                it != InstagramScreen.EXPLORE && it != InstagramScreen.HOME_FEED }) {
            assertEquals(screen.name, 266,
                InstagramNavigationGeometry.top(266, 98, screen, true, true))
        }
    }
}
