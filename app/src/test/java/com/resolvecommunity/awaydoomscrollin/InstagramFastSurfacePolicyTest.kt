package com.resolvecommunity.awaydoomscrollin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstagramFastSurfacePolicyTest {
    @Test fun selectedHomeWithItsHeaderIsFastAndProtected() {
        assertEquals(InstagramScreen.HOME_FEED, InstagramFastSurfacePolicy.classify(
            homeSelected = true, searchSelected = false, hasBackNavigation = false,
            hasHomeHeader = true, hasExploreHeader = false, hasExploreGrid = false))
    }

    @Test fun selectedSearchNeedsBothExploreHeaderAndGrid() {
        assertEquals(InstagramScreen.EXPLORE, InstagramFastSurfacePolicy.classify(
            homeSelected = false, searchSelected = true, hasBackNavigation = false,
            hasHomeHeader = false, hasExploreHeader = true, hasExploreGrid = true))
        assertNull(InstagramFastSurfacePolicy.classify(
            homeSelected = false, searchSelected = true, hasBackNavigation = false,
            hasHomeHeader = false, hasExploreHeader = true, hasExploreGrid = false))
    }

    @Test fun viewerOrSearchDestinationWithBackNeverUsesTheFastHostPath() {
        assertNull(InstagramFastSurfacePolicy.classify(
            homeSelected = true, searchSelected = false, hasBackNavigation = true,
            hasHomeHeader = true, hasExploreHeader = false, hasExploreGrid = false))
        assertNull(InstagramFastSurfacePolicy.classify(
            homeSelected = false, searchSelected = true, hasBackNavigation = true,
            hasHomeHeader = false, hasExploreHeader = true, hasExploreGrid = true))
    }
}
